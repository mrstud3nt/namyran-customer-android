<?php
/**
 * Plugin Name: Namyran Customer Stage Bridge
 * Description: Read-only native customer metadata from the installed Core; Stage only.
 * Version: 0.1.0
 */
if (!defined('ABSPATH')) exit;
final class Namyran_Customer_Stage_Bridge {
    public static function enabled(): bool {
        foreach (array(home_url('/'), site_url('/')) as $url) {
            $p=wp_parse_url($url);
            if (($p['scheme']??'')!=='https'||strtolower($p['host']??'')!=='stage.namyranservice.ir'||(!empty($p['port'])&&(int)$p['port']!==443)) return false;
        }
        return class_exists('NSC_Utils') && class_exists('NSC_Catalog') && class_exists('NSC_Geo_Data');
    }
    public static function permission() {
        if (!self::enabled()) return new WP_Error('customer_stage_only','این اتصال فقط در Stage فعال است.',array('status'=>503));
        if (!NSC_Utils::rate_limit('customer_metadata|'.NSC_Utils::ip_hash(),120,10*MINUTE_IN_SECONDS)) return new WP_Error('customer_metadata_limit','چند دقیقه بعد دوباره تلاش کنید.',array('status'=>429));
        return true;
    }
    public static function response(array $data): WP_REST_Response {
        $r=new WP_REST_Response($data);$r->header('Cache-Control','no-store, private');return $r;
    }
    public static function config() {
        // Render only to obtain Core's own current localization. Never duplicate catalog/settings.
        do_shortcode('[namyran_service_request]');
        $script=(string)wp_scripts()->get_data('nsc-frontend','data');
        if (!preg_match('/(?:^|\n)var NSC_DATA = (\{[^\n]*\});(?:\n|$)/',$script,$m)) return new WP_Error('customer_config_unavailable','پیکربندی زنده Core در دسترس نیست.',array('status'=>503));
        $config=json_decode($m[1],true);
        if (!is_array($config)||!isset($config['categories'],$config['services'],$config['tvInstall'])) return new WP_Error('customer_config_invalid','ساختار داده Core پشتیبانی نمی‌شود.',array('status'=>503));
        // Only expose the existing public form data and names of explicitly activated service cities.
        $cities=array();
        foreach (NSC_Geo_Data::active_service_catalog() as $key=>$city) {
            $groups=array();foreach($city['groups']??array() as $group){$areas=array();foreach($group['areas']??array() as $area)$areas[]=array('id'=>(string)$area['id'],'title'=>(string)$area['name']);$groups[]=array('id'=>(string)$group['id'],'title'=>(string)$group['name'],'areas'=>$areas);}
            $cities[]=array('id'=>(string)$key,'title'=>(string)$city['name'],'groups'=>$groups);
        }
        $config['customerMobile']=array('schema'=>1,'coreVersion'=>NSC_VERSION,'cities'=>$cities,'modelMode'=>'free_text','problemMode'=>'free_text','scheduleKind'=>'customer_preference','tvVipSizes'=>array('85 اینچ','100 اینچ'),'maxOrderItems'=>20);
        // Fresh time and catalog digest allow the client to invalidate its selections.
        $config['locationRequired']=in_array($config['locationRequired']??false,array(true,1,'1','true'),true);
        $config['serverNow']=current_datetime()->format(DATE_ATOM);
        $config['revision']=hash('sha256',wp_json_encode(array($config['categories'],$config['services'],$config['brands'],$config['installBrands'],$config['tvInstall'],$cities)));
        unset($config['publicApiNonce']); // Native clients use the existing uncached bootstrap.
        return self::response($config);
    }
    public static function schedule(WP_REST_Request $request) {
        $date=(string)$request->get_param('date');
        if (!NSC_Utils::is_valid_gregorian_date($date)) return new WP_Error('customer_date_invalid','تاریخ معتبر نیست.',array('status'=>400));
        $today=current_datetime()->format('Y-m-d');$parts=array_map('intval',explode('-',$date));$j=NSC_Utils::gregorian_to_jalali(...$parts);$now=NSC_Utils::current_jalali();$max=(int)$now[0]+((int)$now[1]===12?1:0);
        if($date<$today||$j[0]<$now[0]||$j[0]>$max)return new WP_Error('customer_date_outside_range','تاریخ در بازه مجاز Core نیست.',array('status'=>400));
        $slots=array();foreach(NSC_Utils::visit_time_slots() as $value)$slots[]=array('value'=>$value,'available'=>NSC_Utils::is_visit_slot_available($date,$value));
        return self::response(array('date'=>$date,'dateJalali'=>NSC_Utils::format_jalali_date($date),'kind'=>'customer_preference','slots'=>$slots,'serverNow'=>current_datetime()->format(DATE_ATOM)));
    }
    public static function register(): void {
        foreach(array('config'=>'config','schedule'=>'schedule') as $path=>$callback)register_rest_route('namyran-customer/v1','/'.$path,array('methods'=>'GET','callback'=>array(__CLASS__,$callback),'permission_callback'=>array(__CLASS__,'permission')));
    }
}
add_action('rest_api_init',array('Namyran_Customer_Stage_Bridge','register'));
