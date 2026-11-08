package com.sorakoi.sbtranslate.translation;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

/** A failed display-only enhancement must never abort a vanilla packet handler. */
public final class DisplayTranslationSafety {
    private static final AtomicLong LAST_WARNING=new AtomicLong();
    private DisplayTranslationSafety() {}
    public static <T> T preserveOriginal(String context,T original,Supplier<T> operation) {
        try{return operation.get();}
        catch(RuntimeException error) {
            long now=System.currentTimeMillis(),previous=LAST_WARNING.get();
            if(now-previous>=60_000 && LAST_WARNING.compareAndSet(previous,now)) {
                // Never print chat text, click commands, URLs or exception messages.
                var trace=error.getStackTrace();
                LoggerFactory.getLogger("sapphire").warn("Display translation failed in {}; kept original ({} at {}). Further warnings limited to once per minute.",
                    context,error.getClass().getSimpleName(),trace.length==0?"unknown":trace[0]);
            }
            return original;
        }
    }
}
