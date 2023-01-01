package tk.jasoryeh.conductor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface V2FileSystemObjectTypeKey {
    String value();
}
