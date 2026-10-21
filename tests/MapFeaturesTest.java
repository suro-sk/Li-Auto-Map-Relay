package org.li.maprelay;

/** Breaks caught: reversed route coordinates, wrong route mode, double correction,
 * persisted corrupt requests, lost timestamps, unbounded or reordered history. */
public final class MapFeaturesTest {
    private static int assertions;
    private static void check(boolean value,String reason) {
        assertions++; if (!value) throw new AssertionError(reason);
    }
    private static void near(double expected,double actual) {
        check(Math.abs(expected-actual)<0.000003,"Unexpected normalized coordinate: "+actual);
    }
    public static void main(String[] args) {
        MapRequest yerevan=MapRequest.parse("androidamap://route?dlat=40.1772&dlon=44.50349&dname=My%20car&dev=0");
        check(yerevan.yandexUri(MapRequest.Coordinates.AUTO,MapRequest.Mode.WALK).equals(
            "yandexmaps://maps.yandex.ru/?rtext=~40.1772,44.50349&rtt=pd"),"Walking mode must use pd and lat,lon");
        check(yerevan.yandexUri(MapRequest.Coordinates.AUTO,MapRequest.Mode.DRIVE).equals(
            "yandexmaps://maps.yandex.ru/?rtext=~40.1772,44.50349&rtt=auto"),"Driving route link");
        check(yerevan.yandexUri(MapRequest.Coordinates.AUTO,MapRequest.Mode.PIN).equals(
            "yandexmaps://maps.yandex.ru/?pt=44.50349,40.1772&z=17&l=map"),"Pin must use lon,lat");
        check(yerevan.webUri(MapRequest.Coordinates.AUTO,MapRequest.Mode.PIN).equals(
            "https://yandex.com/maps/?pt=44.50349,40.1772&z=17&l=map"),"Shareable HTTPS pin");
        check(yerevan.webUri(MapRequest.Coordinates.AUTO,MapRequest.Mode.WALK).equals(
            "https://yandex.com/maps/?rtext=~40.1772,44.50349&rtt=pd"),"Walking browser fallback");
        check(yerevan.googleUri(MapRequest.Coordinates.AUTO,MapRequest.Mode.DRIVE).equals(
            "google.navigation:q=40.1772,44.50349&mode=d"),"Google driving mode");
        check(yerevan.googleUri(MapRequest.Coordinates.AUTO,MapRequest.Mode.WALK).equals(
            "google.navigation:q=40.1772,44.50349&mode=w"),"Google walking mode");
        MapRequest shifted=MapRequest.parse("androidamap://route?dlat=39.9102264981&dlon=116.4037135824&dev=1");
        near(39.9102264981,shifted.destination(MapRequest.Coordinates.AUTO).latitude);
        near(39.908823,shifted.destination(MapRequest.Coordinates.GCJ02).latitude);
        near(116.39747,shifted.destination(MapRequest.Coordinates.GCJ02).longitude);
        MapRequest bd=MapRequest.parse("androidamap://route?dlat=39.9165658186&dlon=116.4100864544&dev=0");
        near(39.908823,bd.destination(MapRequest.Coordinates.BD09).latitude);
        near(116.39747,bd.destination(MapRequest.Coordinates.BD09).longitude);
        near(39.9165658186,bd.destination(MapRequest.Coordinates.WGS84).latitude);
        check(bd.raw().latitudeText().equals("39.91656582"),"Diagnostics must preserve input coordinate");
        check(bd.coordinateType().equals("GCJ02"),"Diagnostics must expose inferred format");

        RecentLocations history=new RecentLocations();
        check(history.entries().isEmpty(),"History starts empty");
        String first="androidamap://route?dlat=1&dlon=2&dname=A%09B%0AC";
        history.add(first,1000,MapRequest.Coordinates.WGS84);
        history.add("qqmap://map/routeplan?tocoord=3,4",2000,MapRequest.Coordinates.AUTO);
        check(history.entries().get(0).receivedAt==2000,"Newest receipt first");
        check(history.update(first,1000,MapRequest.Coordinates.BD09),"An existing saved receipt must accept a format correction");
        check(history.entries().get(0).receivedAt==2000,"Editing an old location must not make it the newest receipt");
        check(history.entries().get(1).coordinates==MapRequest.Coordinates.BD09,"Corrected format must be stored");
        check(!history.update(first,999,MapRequest.Coordinates.AUTO),"A stale receipt must not edit a newer one");
        history.add(first,3000,MapRequest.Coordinates.BD09);
        check(history.entries().size()==2,"Repeated destination should move to top");
        check(history.entries().get(0).receivedAt==3000,"Duplicate must update receipt timestamp");
        RecentLocations restored=RecentLocations.load(history.serialize());
        check(restored.entries().get(0).uri.equals(first),"Escaped tabs/newlines must survive storage");
        check(restored.entries().get(0).coordinates==MapRequest.Coordinates.BD09,"History preserves selected coordinate interpretation");
        for (int i=0;i<25;i++) history.add("androidamap://route?dlat="+i+"&dlon=2",4000+i,MapRequest.Coordinates.AUTO);
        check(history.entries().size()==20,"History must stay bounded at 20");
        check(RecentLocations.load(history.serialize()).entries().get(0).receivedAt==4024,"Storage must not reverse ordering");
        check(RecentLocations.load("nonsense\n-1\tAUTO\tbad\n123\tAUTO\thttps%3A%2F%2Fevil.example\n").entries().isEmpty(),"Corrupt and non-map records must be dropped");
        history.clear(); check(history.entries().isEmpty()&&history.serialize().isEmpty(),"Clear removes stored destinations");
        check(!history.update(first,1000,MapRequest.Coordinates.AUTO),"Editing after deletion must not restore a record");
        System.out.println("PASS: "+assertions+" route, format, sharing and history assertions");
    }
}
