<?php
if (!defined('ABSPATH')) exit;

final class Namyran_Stage_Customer_Email {
    private static function error(string $code, string $message, int $status): WP_Error {
        return new WP_Error($code, $message, array('status'=>$status));
    }

    private static function identity(WP_REST_Request $request): string {
        $mobile = NSC_Utils::normalize_mobile((string)$request->get_param('mobile'));
        $token = (string)$request->get_param('verification_token');
        if (!NSC_Utils::is_mobile($mobile) || !preg_match('/^[A-Za-z0-9]{48}$/D', $token)) return '';
        $session = get_transient(NSC_Utils::verification_transient_key($token));
        return is_array($session) && ($session['mobile']??'') === $mobile &&
            ($session['purpose']??'') === 'customer_service' ? $mobile : '';
    }

    private static function account_key(string $mobile): string {
        return 'nsc_customer_email_' . hash_hmac('sha256', $mobile, wp_salt('auth'));
    }

    private static function status(string $key): WP_REST_Response {
        $verified = (string)get_option($key, '');
        $pending = get_transient($key);
        return Namyran_Customer_Stage_Bridge::response(array(
            'verified_email'=>$verified,
            'pending_email'=>is_array($pending) ? $pending['email'] : '',
        ));
    }

    public static function profile(WP_REST_Request $request) {
        $mobile = self::identity($request);
        if ($mobile === '') return self::error('customer_email_auth', 'ورود به حساب لازم است.', 403);
        $key = self::account_key($mobile);
        $action = (string)$request->get_param('action');
        if ($action === 'status') return self::status($key);
        if ($action !== 'send') return self::error('customer_email_action', 'عملیات ایمیل معتبر نیست.', 400);
        $email = trim((string)$request->get_param('email'));
        if (strlen($email)>254 || !is_email($email)) return self::error('customer_email_invalid', 'ایمیل معتبر وارد کنید.', 400);
        if ((string)get_option($key,'') === $email) return self::status($key);
        if (!NSC_Utils::rate_limit('customer_email_send|' . $key, 3, HOUR_IN_SECONDS) ||
            !NSC_Utils::rate_limit('customer_email_ip|' . NSC_Utils::ip_hash(), 10, HOUR_IN_SECONDS)) {
            return self::error('customer_email_rate', 'کمی بعد دوباره تلاش کنید.', 429);
        }
        $token = bin2hex(random_bytes(32));
        $digest = hash('sha256', $token);
        $record = array('email'=>$email, 'digest'=>$digest);
        set_transient($key, $record, 30*MINUTE_IN_SECONDS);
        $link = add_query_arg(array('nsc_customer_email'=>$key,'code'=>$token), home_url('/'));
        if (!wp_mail($email, 'تأیید ایمیل نامیران سرویس', "برای تأیید ایمیل این لینک را باز کنید و دکمه تأیید را بزنید:\n$link")) {
            if (get_transient($key) === $record) delete_transient($key);
            return self::error('customer_email_send', 'ارسال لینک تأیید ممکن نشد.', 503);
        }
        return self::status($key);
    }

    public static function confirmation(): void {
        if (!isset($_GET['nsc_customer_email']) || !Namyran_Customer_Stage_Bridge::enabled()) return;
        $key = sanitize_text_field(wp_unslash($_GET['nsc_customer_email']));
        $code = sanitize_text_field(wp_unslash($_GET['code']??''));
        nocache_headers();
        header('Referrer-Policy: no-referrer');
        if (!preg_match('/^nsc_customer_email_[a-f0-9]{64}$/D',$key) || !preg_match('/^[a-f0-9]{64}$/D',$code)) {
            wp_die('پیوند تأیید معتبر نیست.', 'تأیید ایمیل', array('response'=>400));
        }
        $pending = get_transient($key);
        if (!is_array($pending) || !hash_equals($pending['digest'],hash('sha256',$code))) {
            wp_die('پیوند تأیید معتبر نیست یا پایان یافته است.', 'تأیید ایمیل', array('response'=>410));
        }
        if (($_SERVER['REQUEST_METHOD']??'') === 'POST') {
            $nonce = sanitize_text_field(wp_unslash($_POST['_nsc_email']??''));
            if (!wp_verify_nonce($nonce, 'nsc_email_confirm_'.$code)) {
                wp_die('درخواست تأیید معتبر نیست.', 'تأیید ایمیل', array('response'=>403));
            }
            $used = 'nsc_customer_email_used_' . hash('sha256',$code);
            if (!add_option($used, '1', '', false)) {
                wp_die('این پیوند قبلاً استفاده شده است.', 'تأیید ایمیل', array('response'=>409));
            }
            wp_schedule_single_event(time()+HOUR_IN_SECONDS,'nsc_customer_email_remove_used',array($used));
            update_option($key,$pending['email'],false);
            if (get_transient($key) === $pending) delete_transient($key);
            wp_die('ایمیل تأیید شد. به حساب کاربری برگردید و وضعیت ایمیل را به‌روز کنید.', 'تأیید ایمیل', array('response'=>200));
        }
        $form = '<form method="post" dir="rtl"><p>برای تأیید ایمیل روی دکمه زیر بزنید.</p>'
            . wp_nonce_field('nsc_email_confirm_'.$code, '_nsc_email', false, false)
            . '<button type="submit">تأیید ایمیل</button></form>';
        wp_die($form, 'تأیید ایمیل نامیران سرویس', array('response'=>200));
    }

    public static function receipt(WP_REST_Request $request) {
        global $wpdb;
        $mobile = self::identity($request);
        if ($mobile === '') return self::error('customer_email_auth','ورود به حساب لازم است.',403);
        $email = (string)get_option(self::account_key($mobile),'');
        if (!is_email($email)) return Namyran_Customer_Stage_Bridge::response(array('accepted'=>false));
        $id = absint($request->get_param('request_id'));
        $row = $wpdb->get_row($wpdb->prepare(
            "SELECT tracking_code, short_tracking_code FROM {$wpdb->prefix}nsc_requests WHERE id=%d AND mobile=%s AND deleted_at IS NULL",
            $id,$mobile
        ), ARRAY_A);
        $tracking = is_array($row) ? NSC_Utils::public_tracking_code($row) : '';
        if (!$tracking) return self::error('customer_email_receipt_missing','درخواست پیدا نشد.',404);
        $key = 'nsc_customer_receipt_email_' . $id;
        if (!add_option($key,'sending','',false)) {
            return Namyran_Customer_Stage_Bridge::response(array('accepted'=>get_option($key)==='accepted'));
        }
        $accepted = wp_mail($email,'کد پیگیری نامیران سرویس',"درخواست شما ثبت شد.\nکد پیگیری: $tracking");
        if ($accepted) update_option($key,'accepted',false); else delete_option($key);
        return Namyran_Customer_Stage_Bridge::response(array('accepted'=>$accepted));
    }
}
add_action('template_redirect',array(Namyran_Stage_Customer_Email::class,'confirmation'));

add_action('nsc_customer_email_remove_used', static function (string $key) {
    if (preg_match('/^nsc_customer_email_used_[a-f0-9]{64}$/D', $key)) delete_option($key);
});
