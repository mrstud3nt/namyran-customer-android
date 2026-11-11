<?php
// Isolated unit fixtures, never installed on WordPress and never used by the APK.
define('ABSPATH',__DIR__);define('MINUTE_IN_SECONDS',60);define('NSC_VERSION','test-core');
$GLOBALS['host']='stage.namyranservice.ir';$GLOBALS['title']='Current device';$GLOBALS['enabled']=true;
function home_url($path='/'){return 'https://'.$GLOBALS['host'].$path;}
function site_url($path='/'){return home_url($path);}
function wp_parse_url($value){return parse_url($value);}
function get_option($key,$default){return $GLOBALS[$key]??$default;}
function wp_json_encode($value){return json_encode($value);}
function add_action(...$args){}
function register_rest_route(...$args){}
function do_shortcode($value){return '<form>not returned</form>';}
function wp_scripts(){return new class{function get_data(...$args){return 'var NSC_DATA = '.json_encode(array('categories'=>array('group'=>array(array('title'=>$GLOBALS['title']))),'services'=>array(),'tvInstall'=>array(),'brands'=>array(),'installBrands'=>array(),'publicApiNonce'=>'must-not-expose')).';';}};}
function current_datetime(){return new DateTimeImmutable('2026-11-04T10:00:00+03:30');}
class WP_Error{function __construct(public $code,public $message,public $data){} }
class WP_REST_Response{public $headers=array();function __construct(public $data){}function header($k,$v){$this->headers[$k]=$v;}}
class WP_REST_Request{function __construct(public $date){}function get_param($key){return $this->date;}}
class NSC_Utils{
 static function rate_limit(...$args){return $GLOBALS['enabled'];}static function ip_hash(){return 'unit';}
 static function is_valid_gregorian_date($v){return preg_match('/^\d{4}-\d{2}-\d{2}$/D',$v)===1;}
 static function gregorian_to_jalali(...$args){return array(1405,8,13);}static function current_jalali(){return array(1405,8,13);}
 static function visit_time_slots(){return array('from-core');}static function is_visit_slot_available($date,$slot){return $date==='2026-11-05'&&$slot==='from-core';}
 static function format_jalali_date($date){return 'converted-by-core';}
}
class NSC_Catalog{}
class NSC_Geo_Data{
 static function provinces(){return array('province'=>array('name'=>'Province','cities'=>array('active'=>'Active city')));}
static function active_service_catalog(){return array('active'=>array('name'=>'Active city','private_admin_field'=>'secret','groups'=>array(array('id'=>'g','name'=>'Group','areas'=>array(array('id'=>'a','name'=>'Area','private'=>'secret'))))));}}
require dirname(__DIR__).'/namyran-customer-stage-bridge/namyran-customer-stage-bridge.php';
function expect($value,$label){if(!$value)throw new RuntimeException($label);echo "PASS $label\n";}
expect(Namyran_Customer_Stage_Bridge::enabled(),'Stage enabled');
$GLOBALS['host']='namyranservice.ir';expect(!Namyran_Customer_Stage_Bridge::enabled(),'Production refused');
$GLOBALS['host']='stage.namyranservice.ir.evil.test';expect(!Namyran_Customer_Stage_Bridge::enabled(),'Lookalike host refused');
$GLOBALS['host']='stage.namyranservice.ir';$GLOBALS['enabled']=false;expect(Namyran_Customer_Stage_Bridge::permission() instanceof WP_Error,'Rate limit enforced');$GLOBALS['enabled']=true;
$r=Namyran_Customer_Stage_Bridge::config();expect(!isset($r->data['publicApiNonce']),'No cached nonce returned');expect($r->headers['Cache-Control']==='no-store, private','Uncached metadata');expect(strpos(json_encode($r->data),'private_admin_field')===false,'City metadata allowlisted');
$old=$r->data['revision'];$GLOBALS['title']='Changed in Core';$r=Namyran_Customer_Stage_Bridge::config();expect($r->data['revision']!==$old,'Core changes reflected without app update');
expect(Namyran_Customer_Stage_Bridge::schedule(new WP_REST_Request('bad')) instanceof WP_Error,'Invalid date refused');
$r=Namyran_Customer_Stage_Bridge::schedule(new WP_REST_Request('2026-11-05'));expect($r->data['slots'][0]['available']===true,'Availability delegates to Core');expect($r->data['kind']==='customer_preference','No reservation capacity invented');
$GLOBALS['nsc_customer_public_support']=array('national'=>'11111111','cities'=>array('active'=>'22222222','unlisted'=>'33333333'),'provinces'=>array('province'=>'not a number'));
$r=Namyran_Customer_Stage_Bridge::config();
expect($r->data['customerMobile']['geography'][0]['province_id']==='province','Public geography uses Core IDs');
expect($r->data['customerMobile']['support']===array('cities'=>array('active'=>'22222222'),'provinces'=>array(),'national'=>'11111111'),'Only approved-format public numbers with known regions exposed');
