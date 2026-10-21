package org.li.maprelay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Only accepts coordinates; an incoming URI can never choose an arbitrary outgoing URI. */
public final class MapRequest {
    private enum System { WGS84, GCJ02, BD09 }
    public enum Coordinates { AUTO, WGS84, GCJ02, BD09 }
    public enum Mode { PIN, WALK, DRIVE }
    public final String label;
    public final String source;
    private final Point incoming;
    private final System system;

    public static final class Point {
        public final double latitude,longitude;
        Point(double latitude,double longitude) {
            if (Double.isNaN(latitude)||Double.isInfinite(latitude)||Double.isNaN(longitude)||Double.isInfinite(longitude)
                    ||latitude < -90||latitude > 90||longitude < -180||longitude > 180) {
                throw new IllegalArgumentException("Destination coordinates are outside their valid range.");
            }
            this.latitude=latitude; this.longitude=longitude;
        }
        public String latitudeText() { return number(latitude); }
        public String longitudeText() { return number(longitude); }
    }
    private MapRequest(Point point,System system,String label,String source) {
        this.incoming=point; this.system=system; this.source=source;
        String cleaned=(label==null?"":label).replaceAll("[\\p{Cntrl}]"," ").trim();
        if (cleaned.isEmpty()||cleaned.equals("\u672a\u83b7\u53d6\u5230\u4f4d\u7f6e")) cleaned="Car location";
        this.label=cleaned.substring(0,Math.min(160,cleaned.length()));
        // Reject a destination if normalizing its coordinate system exceeds valid bounds.
        // Keep this inside parse(), so the Activity's existing error path handles it.
        destination(true);
    }
    public static MapRequest parse(String input) {
        if (input==null||input.length()>16384) throw new IllegalArgumentException("No valid map destination was received.");
        int colon=input.indexOf(':');
        if (colon<1) throw new IllegalArgumentException("No map link was received.");
        String scheme=input.substring(0,colon).toLowerCase(Locale.ROOT);
        Map<String,String> p=query(input);
        try {
            if (scheme.equals("androidamap")||scheme.equals("amapuri")) {
                String dev=p.containsKey("dev")?p.get("dev"):"0";
                if (!dev.equals("0")&&!dev.equals("1")) throw new IllegalArgumentException("Unsupported Amap coordinate type.");
                String lat=p.containsKey("dlat")?p.get("dlat"):p.get("lat");
                String lon=p.containsKey("dlon")?p.get("dlon"):p.get("lon");
                return new MapRequest(point(lat,lon),dev.equals("1")?System.WGS84:System.GCJ02,
                        p.containsKey("dname")?p.get("dname"):p.get("poiname"),"Amap");
            }
            if (scheme.equals("qqmap")) return new MapRequest(pair(p.get("tocoord")),System.GCJ02,p.get("to"),"Tencent Maps");
            if (scheme.equals("baidumap")) {
                String value=p.get("destination");
                if (value==null) throw new IllegalArgumentException("Baidu destination is missing.");
                String name=null,coordinate=null;
                for (String part:value.split("\\|")) {
                    if (part.startsWith("latlng:")) {
                        if (coordinate!=null) throw new IllegalArgumentException("The Baidu link has ambiguous destination coordinates.");
                        coordinate=part.substring(7);
                    }
                    else if (part.startsWith("name:")) name=part.substring(5);
                    else if (part.contains(",")) {
                        if (coordinate!=null) throw new IllegalArgumentException("The Baidu link has ambiguous destination coordinates.");
                        coordinate=part;
                    }
                }
                String type=p.containsKey("coord_type")?p.get("coord_type"):"bd09ll";
                System crs;
                if (type.equals("bd09ll")) crs=System.BD09;
                else if (type.equals("gcj02")) crs=System.GCJ02;
                else if (type.equals("wgs84")) crs=System.WGS84;
                else throw new IllegalArgumentException("Unsupported Baidu coordinate type.");
                return new MapRequest(pair(coordinate),crs,name,"Baidu Maps");
            }
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("The map link contains invalid destination coordinates."); }
        throw new IllegalArgumentException("This link is not a supported Chinese-map navigation request.");
    }
    private static Point pair(String value) {
        if (value==null) throw new IllegalArgumentException("Destination coordinates are missing.");
        String[] parts=value.split(",",-1);
        if (parts.length!=2) throw new IllegalArgumentException("Expected latitude and longitude.");
        return point(parts[0],parts[1]);
    }
    private static Point point(String lat,String lon) {
        if (lat==null||lon==null||lat.trim().isEmpty()||lon.trim().isEmpty()) throw new IllegalArgumentException("Destination coordinates are missing.");
        return new Point(Double.parseDouble(lat.trim()),Double.parseDouble(lon.trim()));
    }
    private static Map<String,String> query(String input) {
        Map<String,String> result=new LinkedHashMap<String,String>();
        int start=input.indexOf('?');
        if (start<0) return result;
        int end=input.indexOf('#',start);
        String raw=input.substring(start+1,end<0?input.length():end);
        for (String item:raw.split("&")) {
            if (item.isEmpty()) continue;
            int eq=item.indexOf('=');
            String key=decode(eq<0?item:item.substring(0,eq));
            String value=decode(eq<0?"":item.substring(eq+1));
            if (result.containsKey(key)) throw new IllegalArgumentException("The navigation link has duplicate parameters.");
            result.put(key,value);
        }
        return result;
    }
    private static String decode(String value) {
        try { return URLDecoder.decode(value,"UTF-8"); }
        catch (Exception ex) { throw new IllegalArgumentException("The navigation link has invalid text encoding."); }
    }
    private static String encode(String value) {
        try { return URLEncoder.encode(value,"UTF-8").replace("+","%20"); }
        catch (Exception ex) { throw new IllegalArgumentException("Cannot encode destination."); }
    }
    private static String number(double value) {
        return BigDecimal.valueOf(value).setScale(8,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    public Point destination(boolean correct) {
        return destination(correct?Coordinates.AUTO:Coordinates.WGS84);
    }
    public Point raw() { return incoming; }
    public String coordinateType() { return system.name(); }
    public Point destination(Coordinates coordinates) {
        System selected=coordinates==Coordinates.AUTO?system:System.valueOf(coordinates.name());
        if (selected==System.WGS84) return incoming;
        Point gcj=selected==System.BD09?fromBaidu(incoming):incoming;
        if (outside(gcj)) return gcj;
        double lat=gcj.latitude,lon=gcj.longitude;
        // Iterative numerical inverse of the GCJ forward transform; an approximation, not surveyed data.
        for (int i=0;i<8;i++) {
            Point shifted=toGcj(new Point(lat,lon));
            double dlat=shifted.latitude-gcj.latitude,dlon=shifted.longitude-gcj.longitude;
            lat-=dlat; lon-=dlon;
            if (Math.max(Math.abs(dlat),Math.abs(dlon))<1e-9) break;
        }
        return new Point(lat,lon);
    }
    public String geoUri(boolean correct) {
        return geoUri(correct?Coordinates.AUTO:Coordinates.WGS84);
    }
    public String geoUri(Coordinates coordinates) {
        Point p=destination(coordinates); String xy=p.latitudeText()+","+p.longitudeText();
        return "geo:"+xy+"?q="+xy+"%28"+encode(label)+"%29&z=17";
    }
    public String yandexUri(boolean correct) {
        return yandexUri(correct?Coordinates.AUTO:Coordinates.WGS84,Mode.PIN);
    }
    public String yandexUri(Coordinates coordinates,Mode mode) {
        return "yandexmaps://maps.yandex.ru/?"+yandexParameters(coordinates,mode);
    }
    public String webUri(Coordinates coordinates,Mode mode) {
        return "https://yandex.com/maps/?"+yandexParameters(coordinates,mode);
    }
    private String yandexParameters(Coordinates coordinates,Mode mode) {
        Point p=destination(coordinates);
        if (mode==Mode.PIN) return "pt="+p.longitudeText()+","+p.latitudeText()+"&z=17&l=map";
        return "rtext=~"+p.latitudeText()+","+p.longitudeText()+"&rtt="+(mode==Mode.WALK?"pd":"auto");
    }
    public String googleUri(Coordinates coordinates,Mode mode) {
        if (mode==Mode.PIN) return geoUri(coordinates);
        Point p=destination(coordinates);
        return "google.navigation:q="+p.latitudeText()+","+p.longitudeText()+"&mode="+(mode==Mode.WALK?"w":"d");
    }
    private static boolean outside(Point p) {
        return p.longitude<72.004||p.longitude>137.8347||p.latitude<0.8293||p.latitude>55.8271;
    }
    private static Point fromBaidu(Point p) {
        double x=p.longitude-0.0065,y=p.latitude-0.006;
        double z=Math.sqrt(x*x+y*y)-0.00002*Math.sin(y*Math.PI*3000/180);
        double angle=Math.atan2(y,x)-0.000003*Math.cos(x*Math.PI*3000/180);
        return new Point(z*Math.sin(angle),z*Math.cos(angle));
    }
    private static Point toGcj(Point p) {
        double x=p.longitude-105,y=p.latitude-35;
        double dlat=-100+2*x+3*y+0.2*y*y+0.1*x*y+0.2*Math.sqrt(Math.abs(x));
        dlat+=(20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;
        dlat+=(20*Math.sin(y*Math.PI)+40*Math.sin(y/3*Math.PI))*2/3;
        dlat+=(160*Math.sin(y/12*Math.PI)+320*Math.sin(y*Math.PI/30))*2/3;
        double dlon=300+x+2*y+0.1*x*x+0.1*x*y+0.1*Math.sqrt(Math.abs(x));
        dlon+=(20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;
        dlon+=(20*Math.sin(x*Math.PI)+40*Math.sin(x/3*Math.PI))*2/3;
        dlon+=(150*Math.sin(x/12*Math.PI)+300*Math.sin(x/30*Math.PI))*2/3;
        double r=p.latitude*Math.PI/180,sin=Math.sin(r),magic=1-0.00669342162296594323*sin*sin;
        dlat=dlat*180/((6378245*(1-0.00669342162296594323))/(magic*Math.sqrt(magic))*Math.PI);
        dlon=dlon*180/(6378245/Math.sqrt(magic)*Math.cos(r)*Math.PI);
        return new Point(p.latitude+dlat,p.longitude+dlon);
    }
}
