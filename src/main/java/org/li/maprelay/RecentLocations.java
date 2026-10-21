package org.li.maprelay;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Bounded opt-in data. Activity owns whether serialization is persisted. */
public final class RecentLocations {
    public static final class Entry {
        public final String uri;
        public final long receivedAt;
        public final MapRequest.Coordinates coordinates;
        Entry(String uri,long receivedAt,MapRequest.Coordinates coordinates) {
            this.uri=uri; this.receivedAt=receivedAt; this.coordinates=coordinates;
        }
    }
    private final List<Entry> values=new ArrayList<Entry>();
    public List<Entry> entries() { return Collections.unmodifiableList(values); }
    public void clear() { values.clear(); }
    public boolean update(String uri,long time,MapRequest.Coordinates coordinates) {
        for (int index=0;index<values.size();index++) {
            Entry entry=values.get(index);
            if (entry.uri.equals(uri)&&entry.receivedAt==time) {
                MapRequest.parse(uri).destination(coordinates);
                values.set(index,new Entry(uri,time,coordinates)); return true;
            }
        }
        return false;
    }
    public void add(String uri,long time,MapRequest.Coordinates coordinates) {
        if (time<=0||coordinates==null) throw new IllegalArgumentException("Invalid history record");
        MapRequest.parse(uri).destination(coordinates);
        for (Iterator<Entry> i=values.iterator();i.hasNext();) if (i.next().uri.equals(uri)) i.remove();
        values.add(0,new Entry(uri,time,coordinates));
        while (values.size()>20) values.remove(values.size()-1);
    }
    public String serialize() {
        StringBuilder result=new StringBuilder();
        for (Entry value:values) {
            try { result.append(value.receivedAt).append('\t').append(value.coordinates.name()).append('\t')
                    .append(URLEncoder.encode(value.uri,"UTF-8")).append('\n'); }
            catch (java.io.UnsupportedEncodingException ex) { throw new AssertionError(ex); }
        }
        return result.toString();
    }
    public static RecentLocations load(String data) {
        RecentLocations result=new RecentLocations();
        if (data==null||data.length()>1024*1024) return result;
        String[] lines=data.split("\n");
        // add() puts entries first; restoring in reverse preserves receipt order.
        for (int i=Math.min(lines.length,20)-1;i>=0;i--) {
            try {
                String[] fields=lines[i].split("\t",-1);
                if (fields.length!=3) continue;
                result.add(URLDecoder.decode(fields[2],"UTF-8"),Long.parseLong(fields[0]),MapRequest.Coordinates.valueOf(fields[1]));
            } catch (Exception ignored) { /* Discard a corrupt record, never execute it. */ }
        }
        return result;
    }
}
