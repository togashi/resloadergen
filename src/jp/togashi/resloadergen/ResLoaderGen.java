package jp.togashi.resloadergen;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ResLoaderGen {

    @SuppressWarnings("unused")
    private final PrintStream mStdOut = new PrintStream(System.out);
    @SuppressWarnings("unused")
    private final PrintStream mStdErr = new PrintStream(System.err);
    
    private static final String FIELD_TEMPLATE = "\n    public final String <field_name>;";
    private static final String FIELD_ASSIGNMENT_TEMPLATE = "\n        this.<field_name> = context.getString(R.string.<string_id>);";
    
    public static void main(String[] args) {
        (new ResLoaderGen(args)).exec();
    }
    
    private ArrayList<File> mInFiles = new ArrayList<File>();
    private long mLastModified = 0;
    private String mApplicationId;
    private String mOutClassName;
    private File mOutSourceDir;
    
    private enum ARG {
        APP_ID("-a", "--application-id"),
        NAME("-n", "--class-name"),
        DIR("-s", "--src-dir");
        
        private String[] mSw;
        private ARG(String... sws) {
            mSw = sws;
        }
        
        public static ARG fromString(String src) {
            for (ARG e: ARG.values()) {
                for (String sw: e.mSw) {
                    if (sw.equals(src)) {
                        return e;
                    }
                }
            }
            return null;
        }
    }
    
    public ResLoaderGen(String[] args) {
        
        ARG a = null;
        for (String arg: args) {
            if (a == null) {
                a = ARG.fromString(arg);
                if (a == null) {
                    mInFiles.add(new File(arg));
                }
            } else {
                switch (a) {
                    case APP_ID:
                        mApplicationId = arg;
                        break;
                    case DIR:
                        mOutSourceDir = new File(arg);
                        break;
                    case NAME:
                        mOutClassName = arg;
                        break;
                    default:
                        break;
                }
                a = null;
            }
        }
        
        if (a != null) {
            throw new IllegalArgumentException();
        }
        
        if (mApplicationId == null) {
            throw new IllegalArgumentException();
        }
        if (mOutSourceDir == null) {
            throw new IllegalArgumentException();
        }
        if (mOutClassName == null) {
            throw new IllegalArgumentException();
        }
        if (mInFiles.size() == 0) {
            throw new IllegalArgumentException();
        }
        
        
        
    }
    
    private String getOutPackageName() {
        final int pos = mOutClassName.lastIndexOf(".");
        return mOutClassName.substring(0, pos);
    }
    
    private String getOutClassName() {
        final int pos = mOutClassName.lastIndexOf(".");
        return mOutClassName.substring(pos + 1);
    }
    
    private void exec() {
        
        Map<String, String> entries = new HashMap<String, String>();
        for (File file: mInFiles) {
            try {
                long lm = file.lastModified();
                if (lm > mLastModified) mLastModified = lm;
                entries = loadStringResources(file, entries);
            } catch (SAXException e) {
                e.printStackTrace();
            }
        }
        
        StringBuilder declarations = new StringBuilder();
        StringBuilder assignments = new StringBuilder();
        
        for (Entry<String, String> e: entries.entrySet()) {
            declarations.append(FIELD_TEMPLATE.replaceAll("<field_name>", e.getKey().toUpperCase()));
            assignments.append(FIELD_ASSIGNMENT_TEMPLATE.replaceAll("<field_name>", e.getKey().toUpperCase()).replaceAll("<string_id>", e.getKey()));
        }
        
        Map<String, String> expands = new HashMap<String, String>();
        expands.put("<application_id>", mApplicationId);
        expands.put("<package_name>", getOutPackageName());
        expands.put("<name>", getOutClassName());
        expands.put("<field_declarations>", declarations.toString());
        expands.put("<field_assignments>", assignments.toString());
        
        output(expands);
        
    }
    
    private File getClassDir(File srcDir, String packageName) {
        File result = srcDir;
        String[] elms = packageName.split("\\.");
        
        for (String elm: elms) {
            result = new File(result, elm);
        }
        
        return result;
    }
    
    private void output(Map<String, String> params) {
        
        File outDir = getClassDir(mOutSourceDir, getOutPackageName());
        File outFile = new File(outDir, getOutClassName() + ".java");
        if (outFile.exists() && outFile.lastModified() >= mLastModified) {
            // UP-TO-DATE
            return;
        }
        
        String template;
        
        try {
            template = loadTemplate();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return;
        }
        
        for (Entry<String, String> param: params.entrySet()) {
            template = template.replaceAll(param.getKey(), param.getValue());
        }
        
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        
        if (!outFile.exists()) {
            try {
                outFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        
        try {
            saveToFile(outFile, template);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        
    }
    
    private void saveToFile(File outFile, String template) throws FileNotFoundException {
        
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(outFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter bw = new BufferedWriter(osw);
            bw.write(template);
            bw.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    private String loadTemplate() throws UnsupportedEncodingException {
        StringBuilder bldr = new StringBuilder();
        
        InputStream is = getClass().getResourceAsStream("template.txt");
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        
        try {
            
            while (br.ready()) {
                bldr.append(br.readLine());
                bldr.append("\n");
            }
            
            br.close();
            is.close();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return bldr.toString();
    }
    
    private Map<String, String> loadStringResources(File xmlResource, Map<String, String> entries) throws SAXException {
        
        final Map<String, String> fEntries = new HashMap<String, String>();
        if (entries != null) {
            fEntries.putAll(entries);
        }
        
        try {
            SAXParser parser = (SAXParserFactory.newInstance()).newSAXParser();
            
            parser.parse(xmlResource, new DefaultHandler() {
                
                private Stack<String> mPath = new Stack<String>();
                private String mId;
                
                private boolean pathMatches(String... elms) {
                    
                    if (mPath.size() != elms.length) {
                        return false;
                    }
                    
                    for (int i = 0, ix = mPath.size(); i < ix; i++) {
                        if (!mPath.get(i).equals(elms[i])) {
                            return false;
                        }
                    }
                    
                    return true;
                }
                
                @Override
                public void startElement(String arg0, String arg1, String arg2, Attributes arg3) throws SAXException {
                    mPath.push(arg2);
                    
                    if (pathMatches("resources", "string")) {
                        mId = arg3.getValue("name");
                    }
                }
                
                @Override
                public void endElement(String arg0, String arg1, String arg2) throws SAXException {
                    mId = null;
                    mPath.pop();
                }
                
                @Override
                public void characters(char[] arg0, int arg1, int arg2) throws SAXException {
                    if (pathMatches("resources", "string")) {
                        String value = new String(arg0, arg1, arg2);
                        fEntries.put(mId, value);
                    }
                }
                
            });
            
            
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return fEntries;
    }

}
