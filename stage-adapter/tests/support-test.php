<?php
// Isolated controller fixtures: no WordPress host, network, credentials or live database.
define('ABSPATH',__DIR__);define('MINUTE_IN_SECONDS',60);define('HOUR_IN_SECONDS',3600);
define('DB_NAME','fixture_clone2');
class WP_Error {function __construct(public $code,public $message,public $data){}}
class WP_REST_Request {function __construct(public $data){}function get_param($k){return $this->data[$k]??null;}}
class WP_REST_Response {function __construct(public $data){}}
class Namyran_Customer_Stage_Bridge {static function response($d){return new WP_REST_Response($d);}static function enabled(){return $GLOBALS['stage'];}}
class NSC_Utils {static function normalize_mobile($m){return $m;}static function is_mobile($m){return preg_match('/^09[0-9]{9}$/D',$m);}static function verification_transient_key($t){return $t;}static function rate_limit(...$a){return $GLOBALS['rate'];}static function ip_hash(){return 'fixture';}}
function add_action(...$a){}function wp_salt($s){return 'fixture-only';}function get_transient($k){return $GLOBALS['sessions'][$k]??false;}
function sanitize_text_field($v){return trim(strip_tags($v));}function sanitize_textarea_field($v){return trim(strip_tags($v));}
function wp_json_encode($v){return json_encode($v);}function wp_slash($v){return is_array($v)?array_map('wp_slash',$v):(is_string($v)?addslashes($v):$v);}function is_wp_error($v){return $v instanceof WP_Error;}
function wp_strip_all_tags($v){return strip_tags($v);}function user_can($id,$cap){return $id===1;}
function clean_post_cache($id){}function clean_comment_cache($id){}
function register_post_type($type,$args){$GLOBALS['registered']=$args;}
function get_post($id){return isset($GLOBALS['posts'][$id])?clone $GLOBALS['posts'][$id]:null;}
function get_post_meta($id,$k,$single){return $GLOBALS['meta'][$id][$k]??'';}
function add_post_meta($id,$k,$v,$unique){if($GLOBALS['fail_meta'])return false;$GLOBALS['meta'][$id][$k]=$v;return 1;}
function get_posts($args){
 $rows=[];foreach($GLOBALS['posts'] as $id=>$p){if($p->post_status!==$args['post_status']||$p->post_type!==$args['post_type'])continue;
 $v=$GLOBALS['meta'][$id][$args['meta_key']]??null;if($v===null||isset($args['meta_value'])&&$v!==$args['meta_value'])continue;$rows[]=clone $p;}
 return array_slice(array_reverse($rows),0,$args['numberposts']);
}
function wp_insert_post($data,$errors){$id=++$GLOBALS['sequence'];$GLOBALS['posts'][$id]=(object)array_merge($data,['ID'=>$id,'post_title'=>stripslashes($data['post_title']),'post_content'=>stripslashes($data['post_content'])]);$GLOBALS['meta'][$id]=$data['meta_input'];return $id;}
function wp_insert_comment($data){$data=array_map(fn($v)=>is_string($v)?stripslashes($v):$v,$data);$id=count($GLOBALS['comments'])+1;$GLOBALS['comments'][$id]=(object)array_merge($data,['comment_ID'=>$id]);return $id;}
function get_comments($args){$rows=array_filter($GLOBALS['comments'],fn($x)=>$x->comment_post_ID===$args['post_id']&&$x->comment_approved===1);return array_reverse(array_values($rows));}
class FixtureDB {
 public $prefix='wp_fixture_',$posts='wp_posts',$postmeta='wp_postmeta',$comments='wp_comments',$commentmeta='wp_commentmeta',$last_error='';private $snapshot;
 function prepare($sql,...$args){return ['sql'=>$sql,'args'=>$args];}
 function get_var($q){if(str_contains($q['sql'],'ENGINE'))return $GLOBALS['engine'];if(str_contains($q['sql'],'GET_LOCK'))return $GLOBALS['lock']?'1':'0';return '1';}
 function get_row($q){return get_post($q['args'][0]);}
 function query($q){
  if($q==='START TRANSACTION')$this->snapshot=unserialize(serialize([$GLOBALS['posts'],$GLOBALS['meta'],$GLOBALS['comments']]));
  if($q==='ROLLBACK')[$GLOBALS['posts'],$GLOBALS['meta'],$GLOBALS['comments']]=$this->snapshot;
  if($q==='COMMIT'&&$GLOBALS['fail_commit'])return false;
  return 1;
 }
}
$GLOBALS['wpdb']=new FixtureDB();$GLOBALS['posts']=[];$GLOBALS['meta']=[];$GLOBALS['comments']=[];$GLOBALS['sequence']=100;$GLOBALS['rate']=true;$GLOBALS['lock']=true;$GLOBALS['engine']='InnoDB';$GLOBALS['fail_meta']=false;$GLOBALS['fail_commit']=false;$GLOBALS['stage']=true;
require dirname(__DIR__).'/namyran-customer-stage-bridge/customer-support.php';
function expect($test,$label){if(!$test)throw new RuntimeException($label);echo "PASS $label\n";}
$token=str_repeat('a',48);$other=str_repeat('b',48);$mobile='09123456789';$GLOBALS['sessions']=[$token=>['mobile'=>$mobile,'purpose'=>'customer_service'],$other=>['mobile'=>'09987654321','purpose'=>'customer_service']];
function call_support($input,$credential=null,$number=null){global $token,$mobile;return Namyran_Stage_Customer_Support::handle(new WP_REST_Request(['mobile'=>$number??$mobile,'verification_token'=>$credential??$token,'input'=>$input]));}
function err($r,$code){return $r instanceof WP_Error&&$r->code===$code;}
$create=['action'=>'create_ticket','subject'=>'Fixture subject','message'=>'Fixture message','idempotency_key'=>'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'];
expect(err(call_support($create,'invalid'),'customer_support_auth'),'invalid session rejected');
expect(err(call_support($create,$token,'09987654321'),'customer_support_auth'),'mobile/session mismatch rejected');
$GLOBALS['sessions'][$token]['purpose']='technician';expect(err(call_support($create),'customer_support_auth'),'technician-purpose session rejected');$GLOBALS['sessions'][$token]['purpose']='customer_service';
$r=call_support($create);$id=$r->data['ticket_id'];expect(count($GLOBALS['posts'])===1,'ticket persisted');
expect(call_support($create)->data['ticket_id']===$id&&count($GLOBALS['posts'])===1,'create replay returns same ticket');
$changed=$create;$changed['message']='Changed payload';expect(err(call_support($changed),'customer_support_conflict'),'reused key with different body rejected');
expect(count(call_support(['action'=>'tickets'])->data['tickets'])===1,'owner sees ticket');
expect(count(call_support(['action'=>'tickets'],$other,'09987654321')->data['tickets'])===0,'other customer cannot list ticket');
expect(err(call_support(['action'=>'messages','ticket_id'=>$id],$other,'09987654321'),'customer_support_missing'),'other customer cannot read ticket');
$reply=['action'=>'send_reply','ticket_id'=>$id,'message'=>'Reply "quoted" C:\\fixture','idempotency_key'=>'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'];
expect(err(call_support($reply,$other,'09987654321'),'customer_support_missing'),'other customer cannot reply');
$r=call_support($reply);expect(count($r->data['messages'])===2,'reply persisted');
expect($r->data['messages'][1]['message']===$reply['message'],'quotes and backslashes survive WordPress unslashing');
expect(count(call_support($reply)->data['messages'])===2&&count($GLOBALS['comments'])===1,'reply replay is idempotent');
$GLOBALS['posts'][(int)$id]->comment_status='closed';$reply['idempotency_key']='cccccccc-cccc-cccc-cccc-cccccccccccc';expect(err(call_support($reply),'customer_support_closed'),'closed ticket cannot receive reply');$GLOBALS['posts'][(int)$id]->comment_status='open';
$GLOBALS['comments'][2]=(object)['comment_ID'=>2,'comment_post_ID'=>(int)$id,'comment_approved'=>1,'comment_type'=>'comment','user_id'=>1,'comment_content'=>'Staff answer'];
$GLOBALS['comments'][3]=(object)['comment_ID'=>3,'comment_post_ID'=>(int)$id,'comment_approved'=>1,'comment_type'=>'comment','user_id'=>22,'comment_content'=>'Unprivileged comment'];
$r=call_support(['action'=>'messages','ticket_id'=>$id]);expect(count($r->data['messages'])===3&&$r->data['messages'][2]['sender']==='support','only administrator replies are exposed');
expect(call_support(['action'=>'chat'])->data['online']===false,'no fictitious operator presence');
$GLOBALS['engine']='MyISAM';expect(err(call_support($reply),'customer_support_engine'),'nontransactional storage refused');$GLOBALS['engine']='InnoDB';
$GLOBALS['lock']=false;expect(err(call_support($reply),'customer_support_busy'),'busy lock refused');$GLOBALS['lock']=true;
$GLOBALS['fail_meta']=true;expect(err(call_support($reply),'customer_support_storage')&&count($GLOBALS['comments'])===3,'metadata failure rolls back reply');$GLOBALS['fail_meta']=false;
$GLOBALS['fail_commit']=true;expect(err(call_support($reply),'customer_support_storage')&&count($GLOBALS['comments'])===3,'failed commit rolls back');$GLOBALS['fail_commit']=false;
$bad=$create;$bad['message']=str_repeat('x',4001);expect(err(call_support($bad),'customer_support_input'),'oversize message rejected');
$bad=$create;$bad['message']=[];expect(err(call_support($bad),'customer_support_input'),'non-string message rejected');
$GLOBALS['rate']=false;expect(err(call_support($create),'customer_support_rate'),'rate limit enforced');$GLOBALS['rate']=true;
$GLOBALS['stage']=false;Namyran_Stage_Customer_Support::register_type();expect(!isset($GLOBALS['registered']),'Production does not register ticket UI');
$GLOBALS['stage']=true;Namyran_Stage_Customer_Support::register_type();expect($GLOBALS['registered']['public']===false&&$GLOBALS['registered']['show_in_rest']===false,'tickets hidden from public WordPress routes');
expect(count(array_unique($GLOBALS['registered']['capabilities']))===1&&$GLOBALS['registered']['capabilities']['read_post']==='manage_options','admin capabilities required');
