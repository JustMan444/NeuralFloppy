package tool;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Класс для работы с токенизатором
 */

public class TokenCounter {

    private static Encoding encoding = null;

    private static Encoding getEncoding() {
        if(encoding == null) {
            EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();
            encoding = encodingRegistry.getEncoding(EncodingType.CL100K_BASE);
        }
        return encoding;
    }
    public static int count(String text) {
        if(text == null || text.isEmpty()) return 0;
        return getEncoding().encode(text).size();
    }

}
