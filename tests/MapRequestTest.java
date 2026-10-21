package org.li.maprelay;

public final class MapRequestTest {
    private static int assertions;
    private static void near(double expected, double actual, double tolerance) {
        assertions++;
        if (Math.abs(expected-actual)>tolerance || Double.isNaN(actual)) throw new AssertionError(expected+" != "+actual);
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void invalid(String value) {
        assertions++;
        try { MapRequest.parse(value); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted invalid navigation request");
    }
    public static void main(String[] args) {
        MapRequest yerevan=MapRequest.parse("androidamap://route?sourceApplication=appName&slat=&slon=&sname=My location&dlat=40.1772&dlon=44.50349&dname=My car&dev=0&t=2");
        MapRequest.Point point=yerevan.destination(true);
        near(40.1772,point.latitude,1e-10); near(44.50349,point.longitude,1e-10);
        check(yerevan.yandexUri(true).contains("pt=44.50349,40.1772"),"Yandex placemark order must be lon,lat");
        check(yerevan.geoUri(true).startsWith("geo:40.1772,44.50349?"),"Geo order must be lat,lon");
        check(yerevan.geoUri(true).contains("My%20car"),"Map label must be encoded");
        MapRequest chinese=MapRequest.parse("androidamap://route?dlat=39.9102264981&dlon=116.4037135824&dev=0");
        near(39.908823,chinese.destination(true).latitude,0.000001);
        near(116.39747,chinese.destination(true).longitude,0.000001);
        near(39.9102264981,chinese.destination(false).latitude,1e-10);
        MapRequest gps=MapRequest.parse("amapuri://route/plan/?dlat=39.908823&dlon=116.39747&dev=1");
        near(39.908823,gps.destination(true).latitude,1e-10);
        MapRequest tencent=MapRequest.parse("qqmap://map/routeplan?type=drive&to=Car&tocoord=40.1772,44.50349");
        near(40.1772,tencent.destination(true).latitude,1e-10);
        near(44.50349,tencent.destination(true).longitude,1e-10);
        MapRequest baidu=MapRequest.parse("baidumap://map/direction?destination=latlng:39.9165658186,116.4100864544|name:Car&mode=driving");
        near(39.908823,baidu.destination(true).latitude,0.000003);
        near(116.39747,baidu.destination(true).longitude,0.000003);
        MapRequest unicode=MapRequest.parse("androidamap://route?dlat=-33.86&dlon=151.2&dname=%D0%9C%D0%B0%D1%88%D0%B8%D0%BD%D0%B0%20%26%20A%2BB&dev=1");
        check(unicode.label.equals("\u041c\u0430\u0448\u0438\u043d\u0430 & A+B"),"UTF-8 and encoded separators must survive");
        check(!unicode.geoUri(true).contains(" & "),"Unescaped query injection");
        MapRequest fallback=MapRequest.parse("androidamap://route?dlat=1&dlon=2&dname=%E6%9C%AA%E8%8E%B7%E5%8F%96%E5%88%B0%E4%BD%8D%E7%BD%AE");
        check(fallback.label.equals("Car location"),"Missing-address fallback should not become a misleading place name");
        near(0,MapRequest.parse("androidamap://route?dlat=0&dlon=0").destination(true).latitude,0);
        invalid(null); invalid(""); invalid("https://example.com/?dlat=1&dlon=2");
        invalid("androidamap://route?dlat=1"); invalid("androidamap://route?dlat=NaN&dlon=2");
        invalid("androidamap://route?dlat=Infinity&dlon=2"); invalid("androidamap://route?dlat=91&dlon=2");
        invalid("androidamap://route?dlat=1&dlon=-181"); invalid("androidamap://route?dlat=1&dlat=2&dlon=3");
        invalid("androidamap://route?dlat=1&dlon=2&dname=%ZZ"); invalid("qqmap://map/routeplan?tocoord=1,2,3");
        invalid("baidumap://map/direction?destination=name:Somewhere");
        invalid("androidamap://route?dlat=1&dlon=2&dev=7");
        invalid("baidumap://map/direction?destination=latlng:1,2|latlng:3,4");
        invalid("baidumap://map/direction?destination=1,2|latlng:3,4");
        invalid("baidumap://map/direction?destination=latlng:1,2|3,4");
        invalid("baidumap://map/direction?destination=latlng:-90,-180");
        System.out.println("PASS: "+assertions+" coordinate, protocol and validation assertions");
    }
}
