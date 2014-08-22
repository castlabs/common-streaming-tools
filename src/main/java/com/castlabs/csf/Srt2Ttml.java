package com.castlabs.csf;

import java.io.*;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Srt2Ttml {
    static String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<?access-control allow=\"*\"?>\n" +
            "<?xml-stylesheet href=\"ttml.css\" type=\"text/css\"?>\n" +
            "<tt xml:lang=\"LANGUAGE\" xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" xmlns:tts=\"http://www.w3.org/ns/ttml#styling\" xmlns:ttp=\"http://www.w3.org/ns/ttml#parameter\" \n" +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:smpte=\"http://www.smpte-ra.org/schemas/2052-1/2010/smpte-tt\"  tts:extent=\"854px 480px\">\n" +
            "  <head>\n" +
            "    <metadata>\n" +
            "      <ttm:title>Sintel LANGUAGE</ttm:title>\n" +
            "    </metadata>\n" +
            "        <ttp:profile use=\"http://www.decellc.org/profile/cff-tt-text-2.0.0\"></ttp:profile>\n" +
            "    <layout>\n" +
            "      <region xml:id=\"subtitleArea\" tts:origin=\"10% 80%\" tts:extent=\"80% 20%\" tts:padding=\"5px 5px\" tts:backgroundColor=\"black\" tts:opacity=\"0.85\" tts:displayAlign=\"center\" tts:showBackground=\"whenActive\" tts:wrapOption=\"wrap\"/>\n" +
            "    </layout>\n" +
            "  </head>\n" +
            "  <body>\n" +
            "    <div region=\"subtitleArea\">";
    static String linePattern = "      <p begin=\"%s\" end=\"%s\">%s</p>";
    static String footer = "\n    </div>\n" +
            "  </body>\n" +
            "</tt>\n";

    public static void main(String[] args) throws IOException {

        for (File f : Arrays.asList(
                new File("C:\\dev\\common-streaming-tools2\\test-content\\sintel_de.srt"),
                new File("C:\\dev\\common-streaming-tools2\\test-content\\sintel_en.srt"),
                new File("C:\\dev\\common-streaming-tools2\\test-content\\sintel_es.srt"),
                new File("C:\\dev\\common-streaming-tools2\\test-content\\sintel_fr.srt"),
                new File("C:\\dev\\common-streaming-tools2\\test-content\\sintel_it.srt"),
                new File("C:\\dev\\common-streaming-tools2\\test-content\\sintel_nl.srt"),
                new File("C:\\dev\\common-streaming-tools2\\test-content\\sintel_pl.srt"),
                new File("C:\\dev\\common-streaming-tools2\\test-content\\sintel_pt.srt"),
                new File("C:\\dev\\common-streaming-tools2\\test-content\\sintel_ru.srt"))) {
            convert(f);
        }
    }

    public static void convert(File f) throws IOException {
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new FileInputStream(f), "utf8"));
        int state = 0;
        String line = "";

        // state 0: await start line
        // state 1: await timing
        // state 2: await message

        Pattern timing = Pattern.compile("([0-9][0-9]:[0-9][0-9]:[0-9][0-9](,[0-9][0-9]?[0-9]?)?) --> ([0-9][0-9]:[0-9][0-9]:[0-9][0-9](,[0-9][0-9]?[0-9]?)?)");
        Pattern startLine = Pattern.compile("[0-9]?[0-9]?[0-9]?[0-9]?");

        String startTime = null;
        String endTime = null;
        String message = null;
        String lang = f.getName().replace("sintel_", "");
        lang = lang.replace(".srt", "");

        String document = header.replace("LANGUAGE", lang);

        while ((line = lnr.readLine()) != null) {
            switch (state) {
                case 0:
                    if (startLine.matcher(line).matches()) {
                        state = 1;
                    }
                    break;
                case 1:
                    Matcher m = timing.matcher(line);
                    if (m.matches()) {
                        startTime = m.group(1).replace(",", ".");
                        endTime = m.group(3).replace(",", ".");
                        state = 2;
                    }
                    break;
                case 2:
                    if ("".equals(line.trim())) {
                        document = document + "\n" + String.format(linePattern, startTime, endTime, message);
                        startTime = endTime = message = null;
                        state = 0;
                    } else if (message == null) {
                        message = line;
                    } else {
                        message += "<br/>" + line;
                    }

            }
        }
        document = document + footer;
        new FileOutputStream(f.getAbsolutePath() + ".xml").write(document.getBytes("utf8"));
    }


}
