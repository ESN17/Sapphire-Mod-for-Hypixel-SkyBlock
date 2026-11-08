package com.sorakoi.sbtranslate.sync;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** Public immutable release names: YYYYMMDD_positive-sequence.zip. */
public record DictionaryVersion(LocalDate date,int sequence) implements Comparable<DictionaryVersion> {
    private static final Pattern NAME=Pattern.compile("([0-9]{8})_([1-9][0-9]{0,8})");
    public static DictionaryVersion parse(String value) {
        var m=NAME.matcher(value);if(!m.matches())throw new IllegalArgumentException("Invalid dictionary version");
        return new DictionaryVersion(LocalDate.parse(m.group(1),DateTimeFormatter.BASIC_ISO_DATE),Integer.parseInt(m.group(2)));
    }
    public static DictionaryVersion filename(String value) {
        if(!value.endsWith(".zip"))throw new IllegalArgumentException("Not a dictionary ZIP");
        return parse(value.substring(0,value.length()-4));
    }
    @Override public String toString(){return date.format(DateTimeFormatter.BASIC_ISO_DATE)+"_"+sequence;}
    public String filename(){return this+".zip";}
    @Override public int compareTo(DictionaryVersion other){int d=date.compareTo(other.date);return d!=0?d:Integer.compare(sequence,other.sequence);}
}
