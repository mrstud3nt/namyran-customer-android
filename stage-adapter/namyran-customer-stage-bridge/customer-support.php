<?php
if (!defined('ABSPATH')) exit;

/** Private WordPress tickets. No activation, schema migration, mail, SMS or cron hook. */
final class Namyran_Stage_Customer_Support {
    const TYPE = 'nsc_customer_ticket';

    private static function error(string $code, int $status): WP_Error {
        return new WP_Error($code, 'عملیات پشتیبانی انجام نشد. دوباره تلاش کنید.', array('status'=>$status));
    }

    public static function register_type(): void {
        if (!Namyran_Customer_Stage_Bridge::enabled()) return;
        register_post_type(self::TYPE, array(
            'label'=>'تیکت‌های مشتریان نامیران', 'public'=>false, 'publicly_queryable'=>false,
            'show_ui'=>true, 'show_in_rest'=>false, 'exclude_from_search'=>true,
            'supports'=>array('title','editor','comments'), 'map_meta_cap'=>false,
            'capabilities'=>array_fill_keys(array('edit_post','read_post','delete_post','edit_posts',
                'edit_others_posts','publish_posts','read_private_posts','delete_posts',
                'delete_private_posts','delete_published_posts','delete_others_posts',
                'edit_private_posts','edit_published_posts','create_posts'), 'manage_options'),
        ));
    }

    public static function owner(WP_REST_Request $request): string {
        $mobile = NSC_Utils::normalize_mobile((string)$request->get_param('mobile'));
        $token = (string)$request->get_param('verification_token');
        if (!NSC_Utils::is_mobile($mobile) || !preg_match('/^[A-Za-z0-9]{48}$/D', $token)) return '';
        $session = get_transient(NSC_Utils::verification_transient_key($token));
        if (!is_array($session) || ($session['purpose']??'')!=='customer_service'
            || ($session['mobile']??'')!==$mobile) return '';
        return hash_hmac('sha256', 'customer-support|'.$mobile, wp_salt('auth'));
    }

    private static function ticket(int $id, string $owner, bool $locked=false) {
        global $wpdb;
        $post = $locked ? $wpdb->get_row($wpdb->prepare("SELECT * FROM {$wpdb->posts} WHERE ID=%d FOR UPDATE",$id)) : get_post($id);
        if ($locked && $wpdb->last_error) throw new RuntimeException('ticket_read');
        return $post && $post->post_type===self::TYPE && $post->post_status==='private'
            && hash_equals($owner, (string)get_post_meta($id, '_nsc_support_owner', true)) ? $post : null;
    }

    private static function messages($post): WP_REST_Response {
        $rows = array(array('id'=>'initial', 'sender'=>'customer', 'message'=>$post->post_content));
        $comments = get_comments(array('post_id'=>$post->ID,'status'=>'approve','order'=>'DESC','number'=>100));
        foreach (array_reverse($comments) as $comment) {
            // Only our customer inserts or replies from an authorized WordPress administrator.
            if ($comment->comment_type!=='nsc_customer_reply'
                && (! $comment->user_id || !user_can((int)$comment->user_id,'manage_options'))) continue;
            $rows[] = array('id'=>(string)$comment->comment_ID,
                'sender'=>$comment->comment_type==='nsc_customer_reply'?'customer':'support',
                'message'=>wp_strip_all_tags($comment->comment_content));
        }
        return Namyran_Customer_Stage_Bridge::response(array('messages'=>$rows,
            'can_reply'=>$post->comment_status==='open', 'history_limit'=>100));
    }

    public static function handle(WP_REST_Request $request) {
        $owner = self::owner($request);
        if ($owner==='') return self::error('customer_support_auth',403);
        if (!NSC_Utils::rate_limit('customer_support|'.$owner.'|'.NSC_Utils::ip_hash(),90,MINUTE_IN_SECONDS))
            return self::error('customer_support_rate',429);
        $input = $request->get_param('input');
        if (!is_array($input)) return self::error('customer_support_input',400);
        $action = $input['action']??'';
        if ($action==='chat') return Namyran_Customer_Stage_Bridge::response(array('online'=>false,'messages'=>array()));
        if ($action==='tickets') {
            $posts = get_posts(array('post_type'=>self::TYPE,'post_status'=>'private','numberposts'=>100,
                'orderby'=>'ID','order'=>'DESC','cache_results'=>false,'meta_key'=>'_nsc_support_owner','meta_value'=>$owner));
            $rows = array();
            foreach ($posts as $post) $rows[]=array('id'=>(string)$post->ID,'code'=>'NSC-'.$post->ID,
                'subject'=>$post->post_title,'status_label'=>$post->comment_status==='open'?'باز':'بسته');
            return Namyran_Customer_Stage_Bridge::response(array('tickets'=>$rows,'history_limit'=>100));
        }
        $rawId = $input['ticket_id']??'';
        if (!is_scalar($rawId)) return self::error('customer_support_input',400);
        $id = preg_match('/^[1-9][0-9]{0,17}$/D',(string)$rawId)?(int)$rawId:0;
        if ($action==='messages') {
            $post = self::ticket($id,$owner);
            return $post ? self::messages($post) : self::error('customer_support_missing',404);
        }
        if (!in_array($action,array('create_ticket','send_reply'),true)) return self::error('customer_support_action',400);
        foreach (array('subject','message','idempotency_key') as $key)
            if (isset($input[$key]) && !is_string($input[$key])) return self::error('customer_support_input',400);
        $subject = sanitize_text_field($input['subject']??'');
        $message = sanitize_textarea_field($input['message']??'');
        $key = $input['idempotency_key']??'';
        if (!preg_match('/^[a-f0-9-]{36}$/Di',$key) || trim($message)==='' || mb_strlen($message)>4000
            || ($action==='create_ticket' && (trim($subject)==='' || mb_strlen($subject)>120)))
            return self::error('customer_support_input',400);
        if (!NSC_Utils::rate_limit('customer_support_write|'.$owner,20,HOUR_IN_SECONDS))
            return self::error('customer_support_rate',429);
        return self::write($owner,$action,$id,$subject,$message,$key);
    }

    private static function write(string $owner,string $action,int $id,string $subject,string $message,string $key) {
        global $wpdb;
        // Atomic idempotency and rollback require transactional WP storage as well as Core tables.
        foreach (array($wpdb->posts,$wpdb->postmeta,$wpdb->comments,$wpdb->commentmeta) as $table) {
            $engine=$wpdb->get_var($wpdb->prepare('SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s',$table));
            if ($wpdb->last_error || strcasecmp((string)$engine,'InnoDB')!==0) return self::error('customer_support_engine',503);
        }
        $lock='nsc_support_'.substr(hash('sha256',DB_NAME.'|'.$wpdb->prefix.'|'.$owner),0,40);
        if ((string)$wpdb->get_var($wpdb->prepare('SELECT GET_LOCK(%s,0)',$lock))!=='1')
            return self::error('customer_support_busy',409);
        $operation=hash('sha256',$owner.'|'.strtolower($key));
        $fingerprint=hash('sha256',wp_json_encode(array($action,$id,$subject,$message)));
        $created=0; $commentId=0; $transaction=false;
        try {
            if ($wpdb->query('START TRANSACTION')===false) throw new RuntimeException('transaction');
            $transaction=true;
            $prior=get_posts(array('post_type'=>self::TYPE,'post_status'=>'private','numberposts'=>1,
                'cache_results'=>false,'meta_key'=>'_nsc_support_operation_'.$operation,'meta_value'=>$fingerprint));
            if ($wpdb->last_error) throw new RuntimeException('operation_read');
            $reused=get_posts(array('post_type'=>self::TYPE,'post_status'=>'private','numberposts'=>1,
                'cache_results'=>false,'meta_key'=>'_nsc_support_operation_'.$operation));
            if ($wpdb->last_error) throw new RuntimeException('operation_read');
            if ($reused && !$prior) { $wpdb->query('ROLLBACK');$transaction=false;return self::error('customer_support_conflict',409); }
            if ($prior) {
                $post=self::ticket((int)$prior[0]->ID,$owner,true);
                if (!$post) throw new RuntimeException('owner');
            } elseif ($action==='create_ticket') {
                $created=wp_insert_post(array('post_type'=>self::TYPE,'post_status'=>'private','post_author'=>0,
                    'post_title'=>wp_slash($subject),'post_content'=>wp_slash($message),'comment_status'=>'open',
                    'meta_input'=>array('_nsc_support_owner'=>$owner)),true);
                if (is_wp_error($created) || !$created) { $created=0; throw new RuntimeException('insert'); }
                $post=get_post($created);
            } else {
                $post=self::ticket($id,$owner,true);
                if (!$post) { $wpdb->query('ROLLBACK');$transaction=false;return self::error('customer_support_missing',404); }
                if ($post->comment_status!=='open') { $wpdb->query('ROLLBACK');$transaction=false;return self::error('customer_support_closed',409); }
                $commentId=wp_insert_comment(wp_slash(array('comment_post_ID'=>$id,'comment_content'=>$message,
                    'comment_type'=>'nsc_customer_reply','comment_approved'=>1,'user_id'=>0,
                    'comment_author'=>'مشتری','comment_author_email'=>'','comment_author_IP'=>'')));
                if (!$commentId) throw new RuntimeException('comment');
            }
            if (!$prior && !add_post_meta($post->ID,'_nsc_support_operation_'.$operation,$fingerprint,true))
                throw new RuntimeException('operation');
            if ($wpdb->query('COMMIT')===false) throw new RuntimeException('commit');
            $transaction=false;
            return $action==='create_ticket'
                ? Namyran_Customer_Stage_Bridge::response(array('ticket_id'=>(string)$post->ID))
                : self::messages($post);
        } catch (Throwable $e) {
            if ($transaction) $wpdb->query('ROLLBACK');
            if ($created) clean_post_cache($created);
            if ($id) clean_post_cache($id);
            if ($commentId) clean_comment_cache($commentId);
            return self::error('customer_support_storage',503);
        } finally { $wpdb->get_var($wpdb->prepare('SELECT RELEASE_LOCK(%s)',$lock)); }
    }
}
add_action('init',array(Namyran_Stage_Customer_Support::class,'register_type'));
add_action('rest_api_init',static function () {
    register_rest_route('namyran-customer/v1','/support',array('methods'=>'POST',
        'callback'=>array(Namyran_Stage_Customer_Support::class,'handle'),
        'permission_callback'=>array(Namyran_Customer_Stage_Bridge::class,'permission')));
});
