package tk.jasoryeh.conductor.processor;

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

                temp = temp.replaceAll(Pattern.quote("{{" + key + "}}"), value); // regular
                temp = temp.replaceAll(Pattern.quote("{{!" + key + "!}}"), value); // strict
            }
            for (Map.Entry<String, String> env : System.getenv().entrySet()) {
                String key = env.getKey();
                String value = env.getValue();

                temp = temp.replaceAll(Pattern.quote("{{" + key + "}}"), value); // regular
                temp = temp.replaceAll(Pattern.quote("{{$" + key + "$}}"), value); // env
            }
        }
        return temp;
    }

}
