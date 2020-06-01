package tk.jasoryeh.conductor.processor;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class VariableResolver {

    /**
     * NEWLINE: Replaces with system newline {NEWLINE}
     * REGULAR: Scans all sources for this include {{varname}} starting with the included variables
     * STRICT('!'): Strictly limited to include configuration {{!varname!}}
     * ENVIRONMENT('$'): Environment only variable {{$varname$}}
     * @param vars variables from configuration
     * @param inText text to scan and replace
     * @return
     */
    public static String resolveVariables(Map<String, String> vars, final String inText, boolean doApply) {
        String temp = inText.replaceAll(Pattern.quote("{NEWLINE}"), System.lineSeparator());
        if(doApply) {
            // vars
            for (Map.Entry<String, String> var : vars.entrySet()) {
                String key = var.getKey();
                String value = var.getValue();

                // regular
                temp = temp.replaceAll(Pattern.quote("{{" + key + "}}"), value);
                // strict
                temp = temp.replaceAll(Pattern.quote("{{!" + key + "!}}"), value);
            }
            for (Map.Entry<String, String> env : System.getenv().entrySet()) {
                String key = env.getKey();
                String value = env.getValue();

                // regular
                temp = temp.replaceAll(Pattern.quote("{{" + key + "}}"), value);
                // env
                temp = temp.replaceAll(Pattern.quote("{{$" + key + "$}}"), value);
            }
        }
        return temp;
    }

    public static void main(String[] args) {
        HashMap<String, String> stringStringHashMap = new HashMap<>();
        stringStringHashMap.put("ASDF", "OT WORKS!!! WOOT WOOT ///// \\ asdf");
        stringStringHashMap.put("STATIC_AUTH", "input:testasdfasdf");

        String s = resolveVariables(stringStringHashMap, "{{ASD_SHOULDSKIPTHIS}} {{STATIC_AUTH}}@but this should work {{ASDF}}, but i think this is an env {{JAVA_HOME}}", true).replace("\\\\", "/");
        System.out.println(System.getenv().toString().replaceAll(", ", ", " + System.lineSeparator()));
        System.out.println(s);
    }

}
