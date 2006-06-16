/** @file
  CollectPCDAction class.

  This action class is to collect PCD information from MSA, SPD, FPD xml file.
  This class will be used for wizard and build tools, So it can *not* inherit
  from buildAction or wizardAction.
 
Copyright (c) 2006, Intel Corporation
All rights reserved. This program and the accompanying materials
are licensed and made available under the terms and conditions of the BSD License
which accompanies this distribution.  The full text of the license may be found at
http://opensource.org/licenses/bsd-license.php
 
THE PROGRAM IS DISTRIBUTED UNDER THE BSD LICENSE ON AN "AS IS" BASIS,
WITHOUT WARRANTIES OR REPRESENTATIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED.

**/
package org.tianocore.build.pcd.action;

import java.io.BufferedReader;                                                    
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.tianocore.DynamicPcdBuildDefinitionsDocument;
import org.tianocore.DynamicPcdBuildDefinitionsDocument.DynamicPcdBuildDefinitions;
import org.tianocore.DynamicPcdBuildDefinitionsDocument.DynamicPcdBuildDefinitions.PcdBuildData;
import org.tianocore.DynamicPcdBuildDefinitionsDocument.DynamicPcdBuildDefinitions.PcdBuildData.SkuInfo;
import org.tianocore.FrameworkModulesDocument;
import org.tianocore.FrameworkPlatformDescriptionDocument;
import org.tianocore.FrameworkPlatformDescriptionDocument.FrameworkPlatformDescription;
import org.tianocore.ModuleSADocument;
import org.tianocore.ModuleSADocument.ModuleSA;
import org.tianocore.PackageSurfaceAreaDocument;
import org.tianocore.PcdBuildDefinitionDocument.PcdBuildDefinition;
import org.tianocore.build.global.GlobalData;
import org.tianocore.build.global.SurfaceAreaQuery;
import org.tianocore.build.pcd.action.ActionMessage;
import org.tianocore.build.pcd.entity.DynamicTokenValue;
import org.tianocore.build.pcd.entity.MemoryDatabaseManager;
import org.tianocore.build.pcd.entity.SkuInstance;
import org.tianocore.build.pcd.entity.Token;
import org.tianocore.build.pcd.entity.UsageInstance;
import org.tianocore.build.pcd.exception.EntityException;

class StringTable {
    private ArrayList<String>   al; 
    private ArrayList<String>   alComments;
    private String              phase;
    int                         len; 
    int                         bodyStart;
    int                         bodyLineNum;

    public StringTable (String phase) {
        this.phase = phase;
        al = new ArrayList<String>();
        alComments = new ArrayList<String>();
        len = 0;
        bodyStart = 0;
        bodyLineNum = 0;
    }

    public String getSizeMacro () {
        return String.format(PcdDatabase.StringTableSizeMacro, phase, getSize());
    }

    private int getSize () {
        //
        // We have at least one Unicode Character in the table.
        //
        return len == 0 ? 1 : len;
    }

    public int getTableLen () {
        return al.size() == 0 ? 1 : al.size();
    }

    public String getExistanceMacro () {
        return String.format(PcdDatabase.StringTableExistenceMacro, phase, (al.size() == 0)? "TRUE":"FALSE");
    }

    public String getTypeDeclaration () {

        String output;

        final String stringTable = "StringTable";
        final String tab = "\t";
        final String newLine = ";\r\n";

        output =  "/* StringTable */\r\n";

        if (al.size() == 0) {
            output += tab + String.format("UINT16 %s[1] /* StringTable is Empty */", stringTable) + newLine;
        }

        for (int i = 0; i < al.size(); i++) {
            String str = al.get(i);

            if (i == 0) {
                //
                // StringTable is a well-known name in the PCD DXE driver
                //
                output += tab + String.format("UINT16       %s[%d] /* %s */", stringTable, str.length() + 1, alComments.get(i)) + newLine;
            } else {
                output += tab + String.format("UINT16       %s_%d[%d] /* %s */", stringTable, i, str.length() + 1, alComments.get(i)) + newLine;
            }
        }

        return output;

    }

    public ArrayList<String> getInstantiation () {
        ArrayList<String> output = new ArrayList<String>();

        output.add("/* StringTable */"); 

        if (al.size() == 0) {
            output.add("{ 0 }");
        } else {
            String str;

            for (int i = 0; i < al.size(); i++) {
                str = String.format("L\"%s\" /* %s */", al.get(i), alComments.get(i));
                if (i != al.size() - 1) {
                    str += ",";
                }
                output.add(str);
            }
        }

        return output;
    }

    public int add (String str, Token token) {
        int i;

        i = len;
        //
        // Include the NULL character at the end of String
        //
        len += str.length() + 1; 
        al.add(str);
        alComments.add(token.getPrimaryKeyString());

        return i;
    }
}

class SizeTable {
    private ArrayList<Integer>  al;
    private ArrayList<String>   alComments;
    private String              phase;
    private int                 len;
    private int             bodyStart;
    private int             bodyLineNum;

    public SizeTable (String phase) {
        this.phase = phase;
        al = new ArrayList<Integer>();
        alComments = new ArrayList<String>();
        len = 0;
        bodyStart = 0;
        bodyLineNum = 0;
    }

    public String getTypeDeclaration () {
        return String.format(PcdDatabase.SizeTableDeclaration, phase);
    }

    public ArrayList<String> getInstantiation () {
        ArrayList<String> Output = new ArrayList<String>();

        Output.add("/* SizeTable */");
        Output.add("{");
        bodyStart = 2;

        if (al.size() == 0) {
            Output.add("0");
        } else {
            for (int index = 0; index < al.size(); index++) {
                Integer n = al.get(index);
                String str = n.toString();

                if (index != (al.size() - 1)) {
                    str += ",";
                }

                str += " /* " + alComments.get(index) + " */"; 
                Output.add(str);
                bodyLineNum++;
    
            }
        }
        Output.add("}");

        return Output;
    }

    public int getBodyStart() {
        return bodyStart;
    }

    public int getBodyLineNum () {
        return bodyLineNum;
    }

    public int add (Token token) {
        int index = len;

        len++; 
        al.add(token.datumSize);
        alComments.add(token.getPrimaryKeyString());

        return index;
    }
    
    private int getDatumSize(Token token) {
        /*
        switch (token.datumType) {
        case Token.DATUM_TYPE.UINT8:
            return 1;
        default:
            return 0;
        }
        */
        return 0;
    }

    public int getTableLen () {
        return al.size() == 0 ? 1 : al.size();
    }

}

class GuidTable {
    private ArrayList<UUID> al;
    private ArrayList<String> alComments;
    private String          phase;
    private int             len;
    private int             bodyStart;
    private int             bodyLineNum;

    public GuidTable (String phase) {
        this.phase = phase;
        al = new ArrayList<UUID>();
        alComments = new ArrayList<String>();
        len = 0;
        bodyStart = 0;
        bodyLineNum = 0;
    }

    public String getSizeMacro () {
        return String.format(PcdDatabase.GuidTableSizeMacro, phase, getSize());
    }

    private int getSize () {
        return (al.size() == 0)? 1 : al.size();
    }

    public String getExistanceMacro () {
        return String.format(PcdDatabase.GuidTableExistenceMacro, phase, (al.size() == 0)? "TRUE":"FALSE");
    }

    public String getTypeDeclaration () {
        return String.format(PcdDatabase.GuidTableDeclaration, phase);
    }

    private String getUuidCString (UUID uuid) {
        String[]  guidStrArray;

        guidStrArray =(uuid.toString()).split("-");

        return String.format("{ 0x%s, 0x%s, 0x%s, { 0x%s, 0x%s, 0x%s, 0x%s, 0x%s, 0x%s, 0x%s, 0x%s } }",
                                         guidStrArray[0],
                                         guidStrArray[1],
                                         guidStrArray[2],
                                        (guidStrArray[3].substring(0, 2)),
                                        (guidStrArray[3].substring(2, 4)),
                                        (guidStrArray[4].substring(0, 2)),
                                        (guidStrArray[4].substring(2, 4)),
                                        (guidStrArray[4].substring(4, 6)),
                                        (guidStrArray[4].substring(6, 8)),
                                        (guidStrArray[4].substring(8, 10)),
                                        (guidStrArray[4].substring(10, 12))
                                        );
    }

    public ArrayList<String> getInstantiation () {
        ArrayList<String> Output = new ArrayList<String>();

        Output.add("/* GuidTable */");
        Output.add("{");
        bodyStart = 2;

        if (al.size() == 0) {
            Output.add(getUuidCString(new UUID(0, 0)));
        }
        
        for (Object u : al) {
            UUID uuid = (UUID)u;
            String str = getUuidCString(uuid);

            if (al.indexOf(u) != (al.size() - 1)) {
                str += ",";
            }
            Output.add(str);
            bodyLineNum++;

        }
        Output.add("}");

        return Output;
    }

    public int getBodyStart() {
        return bodyStart;
    }

    public int getBodyLineNum () {
        return bodyLineNum;
    }

    public int add (UUID uuid, String name) {
        int index = len;
        //
        // Include the NULL character at the end of String
        //
        len++; 
        al.add(uuid);

        return index;
    }

    public int getTableLen () {
        return al.size() == 0 ? 0 : al.size();
    }

}

class SkuIdTable {
    private ArrayList<Integer[]> al;
    private ArrayList<String>    alComment;
    private String               phase;
    private int                  len;
    private int                   bodyStart;
    private int                   bodyLineNum;

    public SkuIdTable (String phase) {
        this.phase = phase;
        al = new ArrayList<Integer[]>();
        alComment = new ArrayList<String>();
        bodyStart = 0;
        bodyLineNum = 0;
        len = 0;
    }

    public String getSizeMacro () {
        return String.format(PcdDatabase.SkuIdTableSizeMacro, phase, getSize());
    }

    private int getSize () {
        return (al.size() == 0)? 1 : al.size();
    }

    public String getExistanceMacro () {
        return String.format(PcdDatabase.SkuTableExistenceMacro, phase, (al.size() == 0)? "TRUE":"FALSE");
    }

    public String getTypeDeclaration () {
        return String.format(PcdDatabase.SkuIdTableDeclaration, phase);
    }

    public ArrayList<String> getInstantiation () {
        ArrayList<String> Output = new ArrayList<String> ();

        Output.add("/* SkuIdTable */");
        Output.add("{");
        bodyStart = 2;

        if (al.size() == 0) {
            Output.add("0");
        }
        
        for (int index = 0; index < al.size(); index++) {
            String str;

            str = "/* " + alComment.get(index) + "*/ ";
            str += "/* MaxSku */ ";


            Integer[] ia = al.get(index);

            str += ia[0].toString() + ", ";
            for (int index2 = 1; index2 < ia.length; index2++) {
               str += ia[index2].toString();
               if (index != al.size() - 1) {
                   str += ", ";
               }
            }

            Output.add(str);
            bodyLineNum++;

        }

        Output.add("}");

        return Output;
    }

    public int add (Token token) {

        int index;

        Integer [] skuIds = new Integer[token.skuData.size() + 1];
        skuIds[0] = new Integer(token.skuData.size());
        for (index = 1; index < skuIds.length; index++) {
            skuIds[index] = new Integer(token.skuData.get(index - 1).id);
        }

        index = len;

        len += skuIds.length; 
        al.add(skuIds);
        alComment.add(token.getPrimaryKeyString());

        return index;
    }

    public int getTableLen () {
        return al.size() == 0 ? 1 : al.size();
    }

}

class LocalTokenNumberTable {
    private ArrayList<String>    al;
    private ArrayList<String>    alComment;
    private String               phase;
    private int                  len;

    public LocalTokenNumberTable (String phase) {
        this.phase = phase;
        al = new ArrayList<String>();
        alComment = new ArrayList<String>();

        len = 0;
    }

    public String getSizeMacro () {
    	return String.format(PcdDatabase.LocalTokenNumberTableSizeMacro, phase, getSize())
    			+ String.format(PcdDatabase.LocalTokenNumberSizeMacro, phase, al.size());
    }

    public int getSize () {
        return (al.size() == 0)? 1 : al.size();
    }

    public String getExistanceMacro () {
        return String.format(PcdDatabase.DatabaseExistenceMacro, phase, (al.size() == 0)? "TRUE":"FALSE");
    }

    public String getTypeDeclaration () {
        return String.format(PcdDatabase.LocalTokenNumberTableDeclaration, phase);
    }

    public ArrayList<String> getInstantiation () {
        ArrayList<String> output = new ArrayList<String>();

        output.add("/* LocalTokenNumberTable */");
        output.add("{");

        if (al.size() == 0) {
            output.add("0");
        }
        
        for (int index = 0; index < al.size(); index++) {
            String str;

            str = (String)al.get(index);

            str += " /* " + alComment.get(index) + " */ ";


            if (index != (al.size() - 1)) {
                str += ",";
            }

            output.add(str);

        }

        output.add("}");

        return output;
    }

    public int add (Token token) {
        int index = len;
        String str;

        len++; 

        str =  String.format(PcdDatabase.offsetOfStrTemplate, phase, token.hasDefaultValue() ? "Init" : "Uninit", token.getPrimaryKeyString());

        if (token.isUnicodeStringType()) {
            str += " | PCD_TYPE_STRING";
        }

        if (token.isSkuEnable()) {
            str += " | PCD_TYPE_SKU_ENABLED";
        }

        if (token.getDefaultSku().type == DynamicTokenValue.VALUE_TYPE.HII_TYPE) {
            str += " | PCD_TYPE_HII";
        }

        if (token.getDefaultSku().type == DynamicTokenValue.VALUE_TYPE.VPD_TYPE) {
            str += " | PCD_TYPE_VPD";
        }
        
        al.add(str);
        alComment.add(token.getPrimaryKeyString());

        return index;
    }
}

class ExMapTable {

    class ExTriplet {
        public Integer guidTableIdx;
        public Long exTokenNumber;
        public Long localTokenIdx;
    
        public ExTriplet (int guidTableIdx, long exTokenNumber, long localTokenIdx) {
            this.guidTableIdx = new Integer(guidTableIdx);
            this.exTokenNumber = new Long(exTokenNumber);
            this.localTokenIdx = new Long(localTokenIdx);
        }
    }

    private ArrayList<ExTriplet> al;
    private ArrayList<String>    alComment;
    private String               phase;
    private int                  len;
    private int                   bodyStart;
    private int                   bodyLineNum;
    private int                   base;

    public ExMapTable (String phase) {
        this.phase = phase;
        al = new ArrayList<ExTriplet>();
        alComment = new ArrayList<String>();
        bodyStart = 0;
        bodyLineNum = 0;
        len = 0;
    }

    public String getSizeMacro () {
        return String.format(PcdDatabase.ExMapTableSizeMacro, phase, getTableLen())
             + String.format(PcdDatabase.ExTokenNumber, phase, al.size());
    }

    private int getSize () {
        return (al.size() == 0)? 1 : al.size();
    }

    public String getExistanceMacro () {
        return String.format(PcdDatabase.ExMapTableExistenceMacro, phase, (al.size() == 0)? "TRUE":"FALSE");
    }

    public String getTypeDeclaration () {
        return String.format(PcdDatabase.ExMapTableDeclaration, phase);
    }

    public ArrayList<String> getInstantiation () {
        ArrayList<String> Output = new ArrayList<String>();

        Output.add("/* ExMapTable */");
        Output.add("{");
        bodyStart = 2;

        if (al.size() == 0) {
            Output.add("{0, 0, 0}");
        }
        
        int index;
        for (index = 0; index < al.size(); index++) {
            String str;

            ExTriplet e = (ExTriplet)al.get(index);

            str = "{ " + e.exTokenNumber.toString() + ", ";
            str += e.localTokenIdx.toString() + ", ";
            str += e.guidTableIdx.toString();

            str += " /* " + alComment.get(index) + " */";

            if (index != al.size() - 1) {
                str += ",";
            }

            Output.add(str);
            bodyLineNum++;

        }

        Output.add("}");

        return Output;
    }

    public int add (int localTokenIdx, long exTokenNum, int guidTableIdx, String name) {
        int index = len;

        len++; 
        al.add(new ExTriplet(guidTableIdx, exTokenNum, localTokenIdx));
        alComment.add(name);

        return index;
    }

    public int getTableLen () {
        return al.size() == 0 ? 1 : al.size();
    }

}

class PcdDatabase {

    public final static String ExMapTableDeclaration            = "DYNAMICEX_MAPPING ExMapTable[%s_EXMAPPING_TABLE_SIZE];\r\n";
    public final static String GuidTableDeclaration             = "EFI_GUID          GuidTable[%s_GUID_TABLE_SIZE];\r\n";
    public final static String LocalTokenNumberTableDeclaration = "UINT32            LocalTokenNumberTable[%s_LOCAL_TOKEN_NUMBER_TABLE_SIZE];\r\n";
    public final static String StringTableDeclaration           = "UINT16            StringTable[%s_STRING_TABLE_SIZE];\r\n";
    public final static String SizeTableDeclaration             = "UINT16            SizeTable[%s_LOCAL_TOKEN_NUMBER_TABLE_SIZE];\r\n";
    public final static String SkuIdTableDeclaration              = "UINT8             SkuIdTable[%s_SKUID_TABLE_SIZE];\r\n";


    public final static String ExMapTableSizeMacro              = "#define %s_EXMAPPING_TABLE_SIZE  %d\r\n";
    public final static String ExTokenNumber                    = "#define %s_EX_TOKEN_NUMBER       %d\r\n";
    public final static String GuidTableSizeMacro               = "#define %s_GUID_TABLE_SIZE         %d\r\n";
    public final static String LocalTokenNumberTableSizeMacro   = "#define %s_LOCAL_TOKEN_NUMBER_TABLE_SIZE            %d\r\n";
    public final static String LocalTokenNumberSizeMacro   		= "#define %s_LOCAL_TOKEN_NUMBER            %d\r\n";
    public final static String StringTableSizeMacro             = "#define %s_STRING_TABLE_SIZE       %d\r\n";
    public final static String SkuIdTableSizeMacro              = "#define %s_SKUID_TABLE_SIZE        %d\r\n";


    public final static String ExMapTableExistenceMacro         = "#define %s_EXMAP_TABLE_EMPTY    %s\r\n"; 
    public final static String GuidTableExistenceMacro          = "#define %s_GUID_TABLE_EMPTY     %s\r\n";
    public final static String DatabaseExistenceMacro           = "#define %s_DATABASE_EMPTY       %s\r\n";
    public final static String StringTableExistenceMacro        = "#define %s_STRING_TABLE_EMPTY   %s\r\n";
    public final static String SkuTableExistenceMacro           = "#define %s_SKUID_TABLE_EMPTY    %s\r\n";

    public final static String offsetOfSkuHeadStrTemplate       = "offsetof(%s_PCD_DATABASE, %s.%s_SkuDataTable)";
    public final static String offsetOfStrTemplate              = "offsetof(%s_PCD_DATABASE, %s.%s)";

    private StringTable stringTable;
    private GuidTable   guidTable;
    private LocalTokenNumberTable localTokenNumberTable;
    private SkuIdTable  skuIdTable;
    private SizeTable   sizeTable;
    private ExMapTable  exMapTable;

    private ArrayList<Token> alTokens;
    private String phase;
    private int assignedTokenNumber;
    
    //
    // After Major changes done to the PCD
    // database generation class PcdDatabase
    // Please increment the version and please
    // also update the version number in PCD
    // service PEIM and DXE driver accordingly.
    //
    private final int version = 1;

    private String hString;
    private String cString;


    class AlignmentSizeComp implements Comparator<Token> {
        public int compare (Token a, Token b) {
            return getAlignmentSize(b) 
                    - getAlignmentSize(a);
        }
    }

    public PcdDatabase (ArrayList<Token> alTokens, String exePhase, int startLen) {
       phase = exePhase;

       stringTable = new StringTable(phase);
       guidTable = new GuidTable(phase);
       localTokenNumberTable = new LocalTokenNumberTable(phase);
       skuIdTable = new SkuIdTable(phase);
       sizeTable = new SizeTable(phase);
       exMapTable = new ExMapTable(phase); 

       assignedTokenNumber = startLen;
       this.alTokens = alTokens;
    }

    private void getTwoGroupsOfTokens (ArrayList<Token> alTokens, List<Token> initTokens, List<Token> uninitTokens) {
        for (int i = 0; i < alTokens.size(); i++) {
            Token t = (Token)alTokens.get(i);
            if (t.hasDefaultValue()) {
                initTokens.add(t);
            } else {
                uninitTokens.add(t);
            }
        }

        return;
    }

    private int getAlignmentSize (Token token) {
        if (token.getDefaultSku().type == DynamicTokenValue.VALUE_TYPE.HII_TYPE) {
            return 2;
        }

        if (token.getDefaultSku().type == DynamicTokenValue.VALUE_TYPE.VPD_TYPE) {
            return 4;
        }

        if (token.isUnicodeStringType()) {
            return 2;
        }

        switch (token.datumType) {
            case UINT8:
                return 1;
            case UINT16:
                return 2;
            case UINT32:
                return 4;
            case UINT64:
                return 8;
            case POINTER:
                return 1;
            case BOOLEAN:
                return 1;
            }
            return 1;
     }

    public String getCString () {
        return cString;
    }

    public String getHString () {
        return hString;
    }

     public void genCode () 
        throws EntityException {

        final String newLine                        = "\r\n";
        final String declNewLine                    = ";\r\n";
        final String tab                            = "\t";
        final String commaNewLine                   = ", \r\n";

        int i;
        ArrayList<String> decla;
        ArrayList<String> inst;

        String macroStr   = "";
        String initDeclStr = "";
        String initInstStr = "";
        String uninitDeclStr = "";

        List<Token> initTokens = new ArrayList<Token> ();
        List<Token> uninitTokens = new ArrayList<Token> ();
        
        HashMap <String, ArrayList<String>> initCode = new HashMap<String, ArrayList<String>> ();
        HashMap <String, ArrayList<String>> uninitCode = new HashMap<String, ArrayList<String>> ();

        getTwoGroupsOfTokens (alTokens, initTokens, uninitTokens);

        //
        // Generate Structure Declaration for PcdTokens without Default Value
        // PEI_PCD_DATABASE_INIT
        //
        java.util.Comparator<Token> comparator = new AlignmentSizeComp();
        java.util.Collections.sort(initTokens, comparator);
        initCode = processTokens(initTokens);

        //
        // Generate Structure Declaration for PcdTokens without Default Value
        // PEI_PCD_DATABASE_UNINIT
        //
        java.util.Collections.sort(uninitTokens, comparator);
        uninitCode = processTokens(uninitTokens);

        //
        // Generate size info Macro for all Tables
        //
        macroStr += guidTable.getSizeMacro();
        macroStr += stringTable.getSizeMacro();
        macroStr += skuIdTable.getSizeMacro();
        macroStr += localTokenNumberTable.getSizeMacro();
        macroStr += exMapTable.getSizeMacro();

        //
        // Generate existance info Macro for all Tables
        //
        macroStr += guidTable.getExistanceMacro();
        macroStr += stringTable.getExistanceMacro();
        macroStr += skuIdTable.getExistanceMacro();
        macroStr += localTokenNumberTable.getExistanceMacro();
        macroStr += exMapTable.getExistanceMacro();

        //
        // Generate Structure Declaration for PcdTokens with Default Value
        // for example PEI_PCD_DATABASE_INIT
        //
        initDeclStr += "typedef struct {" + newLine;
            {
                initDeclStr += tab + exMapTable.getTypeDeclaration();
                initDeclStr += tab + guidTable.getTypeDeclaration();
                initDeclStr += tab + localTokenNumberTable.getTypeDeclaration();
                initDeclStr += tab + stringTable.getTypeDeclaration();
                initDeclStr += tab + sizeTable.getTypeDeclaration();
                initDeclStr += tab + skuIdTable.getTypeDeclaration();
                if (phase.equalsIgnoreCase("PEI")) {
                    initDeclStr += tab + "SKU_ID            SystemSkuId;" + newLine;
                }

                decla = initCode.get(new String("Declaration"));
                for (i = 0; i < decla.size(); i++)  {
                    initDeclStr += tab + decla.get(i) + declNewLine;
                }

                //
                // Generate Structure Declaration for PcdToken with SkuEnabled
                //
                decla = initCode.get("DeclarationForSku");

                for (i = 0; i < decla.size(); i++) {
                    initDeclStr += tab + decla.get(i) + declNewLine;
                }
            }
        initDeclStr += String.format("} %s_PCD_DATABASE_INIT;\r\n\r\n", phase);

        //
        // Generate MACRO for structure intialization of PCDTokens with Default Value
        // The sequence must match the sequence of declaration of the memembers in the structure
        String tmp = String.format("%s_PCD_DATABASE_INIT g%sPcdDbInit = { ", phase.toUpperCase(), phase.toUpperCase());
        initInstStr +=  tmp + newLine;
        initInstStr += tab + genInstantiationStr(exMapTable.getInstantiation()) + commaNewLine;
        initInstStr += tab + genInstantiationStr(guidTable.getInstantiation()) + commaNewLine;
        initInstStr += tab + genInstantiationStr(localTokenNumberTable.getInstantiation()) + commaNewLine; 
        initInstStr += tab + genInstantiationStr(stringTable.getInstantiation()) + commaNewLine;
        initInstStr += tab + genInstantiationStr(sizeTable.getInstantiation()) + commaNewLine;
        initInstStr += tab + genInstantiationStr(skuIdTable.getInstantiation()) + commaNewLine;
        //
        // For SystemSkuId
        //
        if (phase.equalsIgnoreCase("PEI")) {
            initInstStr += tab + "0" + tab + "/* SystemSkuId */" + commaNewLine;
        }

        inst = initCode.get("Instantiation");
        for (i = 0; i < inst.size(); i++) {
            initInstStr += tab + inst.get(i) + commaNewLine;
        }

        inst = initCode.get("InstantiationForSku");
        for (i = 0; i < inst.size(); i++) {
            initInstStr += tab + inst.get(i);
            if (i != inst.size() - 1) {
                initInstStr += commaNewLine;
            }
        }

        initInstStr += "};";

        uninitDeclStr += "typedef struct {" + newLine;
            {
                decla = uninitCode.get("Declaration");
                if (decla.size() == 0) {
                    uninitDeclStr += "UINT8 dummy /* The UINT struct is empty */" + declNewLine;
                } else {
    
                    for (i = 0; i < decla.size(); i++) {
                        uninitDeclStr += tab + decla.get(i) + declNewLine;
                    }
    
                    decla = uninitCode.get("DeclarationForSku");
    
                    for (i = 0; i < decla.size(); i++) {
                        uninitDeclStr += tab + decla.get(i) + declNewLine;
                    }
                }
            }
        uninitDeclStr += String.format("} %s_PCD_DATABASE_UNINIT;\r\n\r\n", phase);

        cString = initInstStr + newLine;
        hString = macroStr      + newLine  
              + initDeclStr   + newLine
              + uninitDeclStr + newLine
              + newLine;
        
        hString += String.format("#define PCD_%s_SERVICE_DRIVER_VERSION			%d", phase, version);

    }

    private String genInstantiationStr (ArrayList<String> alStr) {
        String str = "";
        for (int i = 0; i< alStr.size(); i++) {
            str += "\t" + alStr.get(i);
            if (i != alStr.size() - 1) {
                str += "\r\n";
            }
        }

        return str;
    }

    private HashMap<String, ArrayList<String>> processTokens (List<Token> alToken) 
        throws EntityException {

        HashMap <String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();

        ArrayList<String> decl = new ArrayList<String>();
        ArrayList<String> declForSkuEnableType = new ArrayList<String>();
        ArrayList<String> inst = new ArrayList<String>();
        ArrayList<String> instForSkuEnableType = new ArrayList<String>();

        for (int index = 0; index < alToken.size(); index++) {
            Token token = alToken.get(index);

            if (token.isSkuEnable()) {
                //
                // BugBug: Schema only support Data type now
                //
                int tableIdx;

                tableIdx = skuIdTable.add(token);

                decl.add(getSkuEnabledTypeDeclaration(token));
                if (token.hasDefaultValue()) {
                    inst.add(getSkuEnabledTypeInstantiaion(token, tableIdx)); 
                }

                declForSkuEnableType.add(getDataTypeDeclarationForSkuEnabled(token));
                if (token.hasDefaultValue()) {
                    instForSkuEnableType.add(getDataTypeInstantiationForSkuEnabled(token));
                }

            } else {
                if (token.getDefaultSku().type == DynamicTokenValue.VALUE_TYPE.HII_TYPE) {
                    decl.add(getVariableEnableTypeDeclaration(token));
                    inst.add(getVariableEnableInstantiation(token));
                } else if (token.getDefaultSku().type == DynamicTokenValue.VALUE_TYPE.VPD_TYPE) {
                    decl.add(getVpdEnableTypeDeclaration(token));
                    inst.add(getVpdEnableTypeInstantiation(token));
                } else if (token.isUnicodeStringType()) {
                    decl.add(getStringTypeDeclaration(token));
                    inst.add(getStringTypeInstantiation(stringTable.add(token.getStringTypeString(), token), token));
                }
                else {
                    decl.add(getDataTypeDeclaration(token));
                    if (token.hasDefaultValue()) {
                        inst.add(getDataTypeInstantiation(token));
                    }
                }
            }

            sizeTable.add(token);
            localTokenNumberTable.add(token);
            token.tokenNumber = assignedTokenNumber++;

        }

        map.put("Declaration",  decl);
        map.put("DeclarationForSku", declForSkuEnableType);
        map.put("Instantiation", inst);
        map.put("InstantiationForSku", instForSkuEnableType);

        return map;
    }

    private String getSkuEnabledTypeDeclaration (Token token) {
        return String.format("SKU_HEAD %s;\r\n", token.getPrimaryKeyString());
    }

    private String getSkuEnabledTypeInstantiaion (Token token, int SkuTableIdx) {

        String offsetof = String.format(PcdDatabase.offsetOfSkuHeadStrTemplate, phase, token.hasDefaultValue()? "Init" : "Uninit", token.getPrimaryKeyString());
        return String.format("{ %s, %d }", offsetof, SkuTableIdx);
    }

    private String getDataTypeDeclarationForSkuEnabled (Token token) {
        String typeStr = "";

        if (token.datumType == Token.DATUM_TYPE.UINT8) {
            typeStr = "UINT8 %s_%s[%d];\r\n";
        } else if (token.datumType == Token.DATUM_TYPE.UINT16) {
            typeStr = "UINT16 %s_%s[%d];\r\n";
        } else if (token.datumType == Token.DATUM_TYPE.UINT32) {
            typeStr = "UINT32 %s_%s[%d];\r\n";
        } else if (token.datumType == Token.DATUM_TYPE.UINT64) {
            typeStr = "UINT64 %s_%s[%d];\r\n";
        } else if (token.datumType == Token.DATUM_TYPE.BOOLEAN) {
            typeStr = "BOOLEAN %s_%s[%d];\r\n";
        } else if (token.datumType == Token.DATUM_TYPE.POINTER) {
            return String.format("UINT8 %s_%s[%d];\r\n", token.getPrimaryKeyString(), "SkuDataTable", token.datumSize * token.skuData.size());
        } 

        return String.format(typeStr, token.getPrimaryKeyString(), "SkuDataTable", token.skuData.size());

    }

    private String getDataTypeInstantiationForSkuEnabled (Token token) {
        String str = "";

        if (token.datumType == Token.DATUM_TYPE.POINTER) {
            return String.format("UINT8 %s_%s[%d]", token.getPrimaryKeyString(), "SkuDataTable", token.datumSize * token.skuData.size());
        } else {
            str = "{ ";
            for (int idx = 0; idx < token.skuData.size(); idx++) {
                str += token.skuData.get(idx).toString();
                if (idx != token.skuData.size() - 1) {
                    str += ", ";
                }
            }
            str += "}";

            return str;
        }

    }

    private String getDataTypeInstantiation (Token token) {

        if (token.datumType == Token.DATUM_TYPE.POINTER) {
            return String.format("%s /* %s */", token.getDefaultSku().value, token.getPrimaryKeyString());
        } else {
            return String.format("%s /* %s */", token.getDefaultSku().value, token.getPrimaryKeyString());
        }
    }


    private String getDataTypeDeclaration (Token token) {

        String typeStr = "";

        if (token.datumType == Token.DATUM_TYPE.UINT8) {
            typeStr = "UINT8";
        } else if (token.datumType == Token.DATUM_TYPE.UINT16) {
            typeStr = "UINT16";
        } else if (token.datumType == Token.DATUM_TYPE.UINT32) {
            typeStr = "UINT32";
        } else if (token.datumType == Token.DATUM_TYPE.UINT64) {
            typeStr = "UINT64";
        } else if (token.datumType == Token.DATUM_TYPE.BOOLEAN) {
            typeStr = "BOOLEAN";
        } else if (token.datumType == Token.DATUM_TYPE.POINTER) {
            return String.format("UINT8 %s[%d]", token.getPrimaryKeyString(), token.datumSize);
        } else {
        }

        return String.format("%s    %s", typeStr, token.getPrimaryKeyString());
    }

    private String getVpdEnableTypeDeclaration (Token token) {
        return String.format("VPD_HEAD %s", token.getPrimaryKeyString());
    }

    private String getVpdEnableTypeInstantiation (Token token) {
        return String.format("{ %s } /* %s */", token.getDefaultSku().vpdOffset,
                                                token.getPrimaryKeyString());
    }

    private String getStringTypeDeclaration (Token token) {
        return String.format("UINT16  %s", token.getPrimaryKeyString());
    }

    private String getStringTypeInstantiation (int StringTableIdx, Token token) {
        return String.format ("%d /* %s */", StringTableIdx,
                                             token.getPrimaryKeyString()); 
    }


    private String getVariableEnableTypeDeclaration (Token token) {
      return String.format("VARIABLE_HEAD  %s", token.getPrimaryKeyString());
    }

    private String getVariableEnableInstantiation (Token token) 
        throws EntityException {
        //
        // Need scott fix
        // 
        return String.format("{ %d, %d, %s } /* %s */", guidTable.add(token.getDefaultSku().variableGuid, token.getPrimaryKeyString()),
                                                        stringTable.add(token.getDefaultSku().getStringOfVariableName(), token),
                                                        token.getDefaultSku().variableOffset, 
                                                        token.getPrimaryKeyString());
    }

    public int getTotalTokenNumber () {
        return sizeTable.getTableLen();
    }

    public static String getPcdDatabaseCommonDefinitions () 
        throws EntityException {

        String retStr = "";
        try {
            File file = new File(GlobalData.getWorkspacePath() + File.separator + 
                                 "Tools" + File.separator + 
                                 "Conf" + File.separator +
                                 "Pcd" + File.separator +
                                 "PcdDatabaseCommonDefinitions.sample");
            FileReader reader = new FileReader(file);
            BufferedReader  in = new BufferedReader(reader);
            String str;
            while ((str = in.readLine()) != null) {
                retStr = retStr +"\r\n" + str;
            }
        } catch (Exception ex) {
            throw new EntityException("Fatal error when generating PcdDatabase Common Definitions");
        }

        return retStr;
    }

    public static String getPcdDxeDatabaseDefinitions () 
        throws EntityException {

        String retStr = "";
        try {
            File file = new File(GlobalData.getWorkspacePath() + File.separator + 
                                 "Tools" + File.separator + 
                                 "Conf" + File.separator +
                                 "Pcd" + File.separator +
                                 "PcdDatabaseDxeDefinitions.sample");
            FileReader reader = new FileReader(file);
            BufferedReader  in = new BufferedReader(reader);
            String str;
            while ((str = in.readLine()) != null) {
                retStr = retStr +"\r\n" + str;
            }
        } catch (Exception ex) {
            throw new EntityException("Fatal error when generating PcdDatabase Dxe Definitions");
        }

        return retStr;
    }

    public static String getPcdPeiDatabaseDefinitions () 
        throws EntityException {

        String retStr = "";
        try {
            File file = new File(GlobalData.getWorkspacePath() + File.separator + 
                                 "Tools" + File.separator + 
                                 "Conf" + File.separator +
                                 "Pcd" + File.separator +
                                 "PcdDatabasePeiDefinitions.sample");
            FileReader reader = new FileReader(file);
            BufferedReader  in = new BufferedReader(reader);
            String str;
            while ((str = in.readLine()) != null) {
                retStr = retStr +"\r\n" + str;
            }
        } catch (Exception ex) {
            throw new EntityException("Fatal error when generating PcdDatabase Pei Definitions");
        }

        return retStr;
    }

}

class ModuleInfo {
    public ModuleSADocument.ModuleSA module;
    public UsageInstance.MODULE_TYPE type;

    public ModuleInfo (ModuleSADocument.ModuleSA module, UsageInstance.MODULE_TYPE type) {
        this.module = module;
        this.type   = type;
    }
}

/** This action class is to collect PCD information from MSA, SPD, FPD xml file.
    This class will be used for wizard and build tools, So it can *not* inherit
    from buildAction or UIAction.
**/
public class CollectPCDAction {
    /// memoryDatabase hold all PCD information collected from SPD, MSA, FPD.
    private MemoryDatabaseManager dbManager;

    /// Workspacepath hold the workspace information.
    private String                workspacePath;

    /// FPD file is the root file. 
    private String                fpdFilePath;

    /// Message level for CollectPCDAction.
    private int                   originalMessageLevel;

    /// Cache the fpd docment instance for private usage.
    private FrameworkPlatformDescriptionDocument fpdDocInstance;

    /**
      Set WorkspacePath parameter for this action class.

      @param workspacePath parameter for this action
    **/
    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
    }

    /**
      Set action message level for CollectPcdAction tool.

      The message should be restored when this action exit.

      @param actionMessageLevel parameter for this action
    **/
    public void setActionMessageLevel(int actionMessageLevel) {
        originalMessageLevel       = ActionMessage.messageLevel;
        ActionMessage.messageLevel = actionMessageLevel;
    }

    /**
      Set FPDFileName parameter for this action class.

      @param fpdFilePath    fpd file path
    **/
    public void setFPDFilePath(String fpdFilePath) {
        this.fpdFilePath = fpdFilePath;
    }

    /**
      Common function interface for outer.
      
      @param workspacePath The path of workspace of current build or analysis.
      @param fpdFilePath   The fpd file path of current build or analysis.
      @param messageLevel  The message level for this Action.
      
      @throws  Exception The exception of this function. Because it can *not* be predict
                         where the action class will be used. So only Exception can be throw.
      
    **/
    public void perform(String workspacePath, String fpdFilePath, 
                        int messageLevel) throws Exception {
        setWorkspacePath(workspacePath);
        setFPDFilePath(fpdFilePath);
        setActionMessageLevel(messageLevel);
        checkParameter();
        execute();
        ActionMessage.messageLevel = originalMessageLevel;
    }

    /**
      Core execution function for this action class.
     
      This function work flows will be:
      1) Collect and prepocess PCD information from FPD file, all PCD
      information will be stored into memory database.
      2) Generate 3 strings for
        a) All modules using Dynamic(Ex) PCD entry.(Token Number)
        b) PEI PCDDatabase (C Structure) for PCD Service PEIM.
        c) DXE PCD Database (C structure) for PCD Service DXE.
                                
      
      @throws  EntityException Exception indicate failed to execute this action.
      
    **/
    private void execute() throws EntityException {
        //
        // Get memoryDatabaseManager instance from GlobalData.
        // The memoryDatabaseManager should be initialized for whatever build
        // tools or wizard tools
        //
        if((dbManager = GlobalData.getPCDMemoryDBManager()) == null) {
            throw new EntityException("The instance of PCD memory database manager is null");
        }

        //
        // Collect all PCD information defined in FPD file.
        // Evenry token defind in FPD will be created as an token into 
        // memory database.
        //
        createTokenInDBFromFPD();
        
        //
        // Call Private function genPcdDatabaseSourceCode (void); ComponentTypeBsDriver
        // 1) Generate for PEI, DXE PCD DATABASE's definition and initialization.
        //
        genPcdDatabaseSourceCode ();
        
    }

    /**
      This function generates source code for PCD Database.
     
      @param void
      @throws EntityException  If the token does *not* exist in memory database.

    **/
    private void genPcdDatabaseSourceCode()
        throws EntityException {
        String PcdCommonHeaderString = PcdDatabase.getPcdDatabaseCommonDefinitions ();

        ArrayList<Token> alPei = new ArrayList<Token> ();
        ArrayList<Token> alDxe = new ArrayList<Token> ();

        dbManager.getTwoPhaseDynamicRecordArray(alPei, alDxe);
        PcdDatabase pcdPeiDatabase = new PcdDatabase (alPei, "PEI", 0);
        pcdPeiDatabase.genCode();
        dbManager.PcdPeimHString        = PcdCommonHeaderString + pcdPeiDatabase.getHString()
                                            + PcdDatabase.getPcdPeiDatabaseDefinitions();
        dbManager.PcdPeimCString        = pcdPeiDatabase.getCString();

        PcdDatabase pcdDxeDatabase = new PcdDatabase (alDxe, 
                                                      "DXE",
                                                      alPei.size()
                                                      );
        pcdDxeDatabase.genCode();
        dbManager.PcdDxeHString   = dbManager.PcdPeimHString + pcdDxeDatabase.getHString()
                                      + PcdDatabase.getPcdDxeDatabaseDefinitions();
        dbManager.PcdDxeCString   = pcdDxeDatabase.getCString();
    }

    /**
      Get component array from FPD.
      
      This function maybe provided by some Global class.
      
      @return List<ModuleInfo> the component array.
      
     */
    private List<ModuleInfo> getComponentsFromFPD() 
        throws EntityException {
        HashMap<String, XmlObject>  map         = new HashMap<String, XmlObject>();
        List<ModuleInfo>            allModules  = new ArrayList<ModuleInfo>();
        ModuleInfo                  current     = null;
        int                         index       = 0;
        org.tianocore.Components    components  = null;
        FrameworkModulesDocument.FrameworkModules fModules = null;
        java.util.List<ModuleSADocument.ModuleSA> modules  = null;
        

        if (fpdDocInstance == null) {
            try {
                fpdDocInstance = (FrameworkPlatformDescriptionDocument)XmlObject.Factory.parse(new File(fpdFilePath));
            } catch(IOException ioE) {
                throw new EntityException("File IO error for xml file:" + fpdFilePath + "\n" + ioE.getMessage());
            } catch(XmlException xmlE) {
                throw new EntityException("Can't parse the FPD xml fle:" + fpdFilePath + "\n" + xmlE.getMessage());
            }

        }

        //
        // Check whether FPD contians <FramworkModules>
        // 
        fModules = fpdDocInstance.getFrameworkPlatformDescription().getFrameworkModules();
        if (fModules == null) {
            return null;
        }

        //
        // BUGBUG: The following is work around code, the final component type should be get from
        // GlobalData class.
        // 
        components = fModules.getSEC();
        if (components != null) {
            modules = components.getModuleSAList();
            for (index = 0; index < modules.size(); index ++) {
                allModules.add(new ModuleInfo(modules.get(index), UsageInstance.MODULE_TYPE.SEC));
            }
        }

        components = fModules.getPEICORE();
        if (components != null) {
            modules = components.getModuleSAList();
            for (index = 0; index < modules.size(); index ++) {
                allModules.add(new ModuleInfo(modules.get(index), UsageInstance.MODULE_TYPE.PEI_CORE));
            }
        }

        components = fModules.getPEIM();
        if (components != null) {
            modules = components.getModuleSAList();
            for (index = 0; index < modules.size(); index ++) {
                allModules.add(new ModuleInfo(modules.get(index), UsageInstance.MODULE_TYPE.PEIM));
            }
        }

        components = fModules.getDXECORE();
        if (components != null) {
            modules = components.getModuleSAList();
            for (index = 0; index < modules.size(); index ++) {
                allModules.add(new ModuleInfo(modules.get(index), UsageInstance.MODULE_TYPE.DXE_CORE));
            }
        }

        components = fModules.getDXEDRIVERS();
        if (components != null) {
            modules = components.getModuleSAList();
            for (index = 0; index < modules.size(); index ++) {
                allModules.add(new ModuleInfo(modules.get(index), UsageInstance.MODULE_TYPE.DXE_DRIVERS));
            }
        }

        components = fModules.getOTHERCOMPONENTS();
        if (components != null) {
            modules = components.getModuleSAList();
            for (index = 0; index < modules.size(); index ++) {
                allModules.add(new ModuleInfo(modules.get(index), UsageInstance.MODULE_TYPE.OTHER_COMPONENTS));
            }
        }
        
        return allModules;
    }

    /**
      Create token instance object into memory database, the token information
      comes for FPD file. Normally, FPD file will contain all token platform 
      informations.
      
      @return FrameworkPlatformDescriptionDocument   The FPD document instance for furture usage.
      
      @throws EntityException                        Failed to parse FPD xml file.
      
    **/
    private void createTokenInDBFromFPD() 
        throws EntityException {
        int                                 index             = 0;
        int                                 index2            = 0;
        int                                 pcdIndex          = 0;
        List<PcdBuildDefinition.PcdData>    pcdBuildDataArray = new ArrayList<PcdBuildDefinition.PcdData>();
        PcdBuildDefinition.PcdData          pcdBuildData      = null;
        Token                               token             = null;
        SkuInstance                         skuInstance       = null;
        int                                 skuIndex          = 0;
        List<ModuleInfo>                    modules           = null;
        String                              primaryKey        = null;
        String                              exceptionString   = null;
        UsageInstance                       usageInstance     = null;
        String                              primaryKey1       = null;
        String                              primaryKey2       = null;
        boolean                             isDuplicate       = false;
        Token.PCD_TYPE                      pcdType           = Token.PCD_TYPE.UNKNOWN;
        Token.DATUM_TYPE                    datumType         = Token.DATUM_TYPE.UNKNOWN;
        int                                 tokenNumber       = 0;
        String                              moduleName        = null;
        String                              datum             = null;
        int                                 maxDatumSize      = 0;

        //
        // ----------------------------------------------
        // 1), Get all <ModuleSA> from FPD file.
        // ----------------------------------------------
        // 
        modules = getComponentsFromFPD();

        if (modules == null) {
            throw new EntityException("[FPD file error] No modules in FPD file, Please check whether there are elements in <FrameworkModules> in FPD file!");
        }

        //
        // -------------------------------------------------------------------
        // 2), Loop all modules to process <PcdBuildDeclarations> for each module.
        // -------------------------------------------------------------------
        // 
        for (index = 0; index < modules.size(); index ++) {
            isDuplicate =  false;
            for (index2 = 0; index2 < index; index2 ++) {
                //
                // BUGBUG: For transition schema, we can *not* get module's version from 
                // <ModuleSAs>, It is work around code.
                // 
                primaryKey1 = UsageInstance.getPrimaryKey(modules.get(index).module.getModuleName(), 
                                                          null,
                                                          null, 
                                                          null, 
                                                          modules.get(index).module.getArch().toString(),
                                                          null);
                primaryKey2 = UsageInstance.getPrimaryKey(modules.get(index2).module.getModuleName(), 
                                                          null, 
                                                          null, 
                                                          null, 
                                                          modules.get(index2).module.getArch().toString(), 
                                                          null);
                if (primaryKey1.equalsIgnoreCase(primaryKey2)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                continue;
            }

    	    //
    	    // It is legal for a module does not contains ANY pcd build definitions.
    	    // 
    	    if (modules.get(index).module.getPcdBuildDefinition() == null) {
                continue;
    	    }
    
            pcdBuildDataArray = modules.get(index).module.getPcdBuildDefinition().getPcdDataList();

            moduleName = modules.get(index).module.getModuleName();

            //
            // ----------------------------------------------------------------------
            // 2.1), Loop all Pcd entry for a module and add it into memory database.
            // ----------------------------------------------------------------------
            // 
            for (pcdIndex = 0; pcdIndex < pcdBuildDataArray.size(); pcdIndex ++) {
                pcdBuildData = pcdBuildDataArray.get(pcdIndex);
                primaryKey   = Token.getPrimaryKeyString(pcdBuildData.getCName(),
                                                         translateSchemaStringToUUID(pcdBuildData.getTokenSpaceGuid()));
                pcdType      = Token.getpcdTypeFromString(pcdBuildData.getItemType().toString());
                datumType    = Token.getdatumTypeFromString(pcdBuildData.getDatumType().toString());
                tokenNumber  = Integer.decode(pcdBuildData.getToken().toString());
                datum        = pcdBuildData.getValue();
                maxDatumSize = pcdBuildData.getMaxDatumSize();

                //
                // Check <TokenSpaceGuid> is exist? In future, because all schema verification will tools
                // will check that, following checking code could be removed.
                // 
                if (pcdBuildData.getTokenSpaceGuid() == null) {
                    exceptionString = String.format("[FPD file error] There is no <TokenSpaceGuid> for PCD %s in module %s! This is required!",
                                                    pcdBuildData.getCName(),
                                                    moduleName);
                    throw new EntityException(exceptionString);
                }

                //
                // -------------------------------------------------------------------------------------------
                // 2.1.1), Do some necessary checking work for FixedAtBuild, FeatureFlag and PatchableInModule
                // -------------------------------------------------------------------------------------------
                // 
                if (!Token.isDynamic(pcdType)) {
                     //
                     // Value is required.
                     // 
                     if (datum == null) {
                         exceptionString = String.format("[FPD file error] There is no value for PCD entry %s in module %s!",
                                                         pcdBuildData.getCName(),
                                                         moduleName);
                         throw new EntityException(exceptionString);
                     }

                     //
                     // Check whether the datum size is matched datum type.
                     // 
                     if ((exceptionString = verifyDatum(pcdBuildData.getCName(), 
                                                        moduleName,
                                                        datum,
                                                        datumType,
                                                        maxDatumSize)) != null) {
                         throw new EntityException(exceptionString);
                     }
                }

                //
                // ---------------------------------------------------------------------------------
                // 2.1.2), Create token or update token information for current anaylized PCD data.
                // ---------------------------------------------------------------------------------
                // 
                if (dbManager.isTokenInDatabase(primaryKey)) {
                    //
                    // If the token is already exist in database, do some necessary checking
                    // and add a usage instance into this token in database
                    // 
                    token = dbManager.getTokenByKey(primaryKey);
    
                    //
                    // checking for DatumType, DatumType should be unique for one PCD used in different
                    // modules.
                    // 
                    if (token.datumType != datumType) {
                        exceptionString = String.format("[FPD file error] The datum type of PCD entry %s is %s, which is different with  %s defined in before!",
                                                        pcdBuildData.getCName(), 
                                                        pcdBuildData.getDatumType().toString(), 
                                                        Token.getStringOfdatumType(token.datumType));
                        throw new EntityException(exceptionString);
                    }

                    //
                    // Check token number is valid
                    // 
                    if (tokenNumber != token.tokenNumber) {
                        exceptionString = String.format("[FPD file error] The token number of PCD entry %s in module %s is different with same PCD entry in other modules!",
                                                        pcdBuildData.getCName(),
                                                        moduleName);
                        throw new EntityException(exceptionString);
                    }

                    //
                    // For same PCD used in different modules, the PCD type should all be dynamic or non-dynamic.
                    // 
                    if (token.isDynamicPCD != Token.isDynamic(pcdType)) {
                        exceptionString = String.format("[FPD file error] For PCD entry %s in module %s, you define dynamic or non-dynamic PCD type which"+
                                                        "is different with others module's",
                                                        token.cName,
                                                        moduleName);
                        throw new EntityException(exceptionString);
                    }

                    if (token.isDynamicPCD) {
                        //
                        // Check datum is equal the datum in dynamic information.
                        // For dynamic PCD, you can do not write <Value> in sperated every <PcdBuildDefinition> in different <ModuleSA>,
                        // But if you write, the <Value> must be same as the value in <DynamicPcdBuildDefinitions>.
                        // 
                        if (!token.isSkuEnable() && 
                            (token.getDefaultSku().type == DynamicTokenValue.VALUE_TYPE.DEFAULT_TYPE) &&
                            (datum != null)) {
                            if (!datum.equalsIgnoreCase(token.getDefaultSku().value)) {
                                exceptionString = String.format("[FPD file error] For dynamic PCD %s in module %s, the datum in <ModuleSA> is "+
                                                                "not equal to the datum in <DynamicPcdBuildDefinitions>, it is "+
                                                                "illega! You could no set <Value> in <ModuleSA> for a dynamic PCD!",
                                                                token.cName,
                                                                moduleName);
                                throw new EntityException(exceptionString);
                            }
                        }

                        if ((maxDatumSize != 0) &&
                            (maxDatumSize != token.datumSize)){
                            exceptionString = String.format("[FPD file error] For dynamic PCD %s in module %s, the max datum size is %d which "+
                                                            "is different with <MaxDatumSize> %d defined in <DynamicPcdBuildDefinitions>!",
                                                            token.cName,
                                                            moduleName,
                                                            maxDatumSize,
                                                            token.datumSize);
                            throw new EntityException(exceptionString);
                        }
                    }
                    
                } else {
                    //
                    // If the token is not in database, create a new token instance and add
                    // a usage instance into this token in database.
                    // 
                    token = new Token(pcdBuildData.getCName(), 
                                      translateSchemaStringToUUID(pcdBuildData.getTokenSpaceGuid()));
    
                    token.datumType     = datumType;
                    token.tokenNumber   = tokenNumber;
                    token.isDynamicPCD  = Token.isDynamic(pcdType);
                    token.datumSize     = maxDatumSize;
                    
                    if (token.isDynamicPCD) {
                        //
                        // For Dynamic and Dynamic Ex type, need find the dynamic information
                        // in <DynamicPcdBuildDefinition> section in FPD file.
                        // 
                        updateDynamicInformation(moduleName, 
                                                 token,
                                                 datum,
                                                 maxDatumSize);
                    }
    
                    dbManager.addTokenToDatabase(primaryKey, token);
                }

                //
                // -----------------------------------------------------------------------------------
                // 2.1.3), Add the PcdType in current module into this Pcd token's supported PCD type.
                // -----------------------------------------------------------------------------------
                // 
                token.updateSupportPcdType(pcdType);

                //
                // ------------------------------------------------
                // 2.1.4), Create an usage instance for this token.
                // ------------------------------------------------
                // 
                usageInstance = new UsageInstance(token, 
                                                  moduleName, 
                                                  null,
                                                  null,
                                                  null,
                                                  modules.get(index).type, 
                                                  pcdType,
                                                  modules.get(index).module.getArch().toString(), 
                                                  null,
                                                  datum,
                                                  maxDatumSize);
                token.addUsageInstance(usageInstance);
            }
        }
    }

    /**
       Verify the datum value according its datum size and datum type, this
       function maybe moved to FPD verification tools in future.
       
       @param cName
       @param moduleName
       @param datum
       @param datumType
       @param maxDatumSize
       
       @return String
     */
    /***/
    public String verifyDatum(String            cName,
                              String            moduleName,
                              String            datum, 
                              Token.DATUM_TYPE  datumType, 
                              int               maxDatumSize) {
        String      exceptionString = null;
        int         value;
        BigInteger  value64;
        String      subStr;
        int         index;

        if (moduleName == null) {
            moduleName = "section <DynamicPcdBuildDefinitions>";
        } else {
            moduleName = "module " + moduleName;
        }

        if (maxDatumSize == 0) {
            exceptionString = String.format("[FPD file error] You maybe miss <MaxDatumSize> for PCD %s in %s",
                                            cName,
                                            moduleName);
            return exceptionString;
        }

        switch (datumType) {
        case UINT8:
            if (maxDatumSize != 1) {
                exceptionString = String.format("[FPD file error] The datum type of PCD data %s in %s "+
                                                "is UINT8, but datum size is %d, they are not matched!",
                                                 cName,
                                                 moduleName,
                                                 maxDatumSize);
                return exceptionString;
            }

            if (datum != null) {
                try {
                    value = Integer.decode(datum);
                } catch (NumberFormatException nfeExp) {
                    exceptionString = String.format("[FPD file error] The datum for PCD %s in %s is not valid "+
                                                    "digital format of UINT8",
                                                    cName,
                                                    moduleName);
                    return exceptionString;
                }
                if (value > 0xFF) {
                    exceptionString = String.format("[FPD file error] The datum for PCD %s in %s is %s exceed"+
                                                    " the max size of UINT8 - 0xFF",
                                                    cName, 
                                                    moduleName,
                                                    datum);
                    return exceptionString;
                }
            }
            break;
        case UINT16:
            if (maxDatumSize != 2) {
                exceptionString = String.format("[FPD file error] The datum type of PCD data %s in %s "+
                                                "is UINT16, but datum size is %d, they are not matched!",
                                                 cName,
                                                 moduleName,
                                                 maxDatumSize);
                return exceptionString;
            }
            if (datum != null) {
                try {
                    value = Integer.decode(datum);
                } catch (NumberFormatException nfeExp) {
                    exceptionString = String.format("[FPD file error] The datum for PCD %s in %s is "+
                                                    "not valid digital of UINT16",
                                                    cName,
                                                    moduleName);
                    return exceptionString;
                }
                if (value > 0xFFFF) {
                    exceptionString = String.format("[FPD file error] The datum for PCD %s in %s is %s "+
                                                    "which exceed the range of UINT16 - 0xFFFF",
                                                    cName, 
                                                    moduleName,
                                                    datum);
                    return exceptionString;
                }
            }
            break;
        case UINT32:
            if (maxDatumSize != 4) {
                exceptionString = String.format("[FPD file error] The datum type of PCD data %s in %s "+
                                                "is UINT32, but datum size is %d, they are not matched!",
                                                 cName,
                                                 moduleName,
                                                 maxDatumSize);
                return exceptionString;
            }

            if (datum != null) {
                try {
                    if (datum.length() > 2) {
                        if ((datum.charAt(0) == '0')        && 
                            ((datum.charAt(1) == 'x') || (datum.charAt(1) == 'X'))){
                            subStr = datum.substring(2, datum.length());
                            value64 = new BigInteger(subStr, 16);
                        } else {
                            value64 = new BigInteger(datum);
                        }
                    } else {
                        value64 = new BigInteger(datum);
                    }
                } catch (NumberFormatException nfeExp) {
                    exceptionString = String.format("[FPD file error] The datum for PCD %s in %s is not "+
                                                    "valid digital of UINT32",
                                                    cName,
                                                    moduleName);
                    return exceptionString;
                }

                if (value64.bitLength() > 32) {
                    exceptionString = String.format("[FPD file error] The datum for PCD %s in %s is %s which "+
                                                    "exceed the range of UINT32 - 0xFFFFFFFF",
                                                    cName, 
                                                    moduleName,
                                                    datum);
                    return exceptionString;
                }
            }
            break;
        case UINT64:
            if (maxDatumSize != 8) {
                exceptionString = String.format("[FPD file error] The datum type of PCD data %s in %s "+
                                                "is UINT64, but datum size is %d, they are not matched!",
                                                 cName,
                                                 moduleName,
                                                 maxDatumSize);
                return exceptionString;
            }

            if (datum != null) {
                try {
                    if (datum.length() > 2) {
                        if ((datum.charAt(0) == '0')        && 
                            ((datum.charAt(1) == 'x') || (datum.charAt(1) == 'X'))){
                            subStr = datum.substring(2, datum.length());
                            value64 = new BigInteger(subStr, 16);
                        } else {
                            value64 = new BigInteger(datum);
                        }
                    } else {
                        value64 = new BigInteger(datum);
                    }
                } catch (NumberFormatException nfeExp) {
                    exceptionString = String.format("[FPD file error] The datum for PCD %s in %s is not valid"+
                                                    " digital of UINT64",
                                                    cName,
                                                    moduleName);
                    return exceptionString;
                }

                if (value64.bitLength() > 64) {
                    exceptionString = String.format("[FPD file error] The datum for PCD %s in %s is %s "+
                                                    "exceed the range of UINT64 - 0xFFFFFFFFFFFFFFFF",
                                                    cName, 
                                                    moduleName,
                                                    datum);
                    return exceptionString;
                }
            }
            break;
        case BOOLEAN:
            if (maxDatumSize != 1) {
                exceptionString = String.format("[FPD file error] The datum type of PCD data %s in %s "+
                                                "is BOOLEAN, but datum size is %d, they are not matched!",
                                                 cName,
                                                 moduleName,
                                                 maxDatumSize);
                return exceptionString;
            }

            if (datum != null) {
                if (!(datum.equalsIgnoreCase("TRUE") ||
                     datum.equalsIgnoreCase("FALSE"))) {
                    exceptionString = String.format("[FPD file error] The datum type of PCD data %s in %s "+
                                                    "is BOOELAN, but value is not 'true'/'TRUE' or 'FALSE'/'false'",
                                                    cName,
                                                    moduleName);
                    return exceptionString;
                }

            }
            break;
        case POINTER:
            if (datum == null) {
                break;
            }

            char    ch     = datum.charAt(0);
            int     start, end;
            String  strValue;
            //
            // For void* type PCD, only three datum is support:
            // 1) Unicode: string with start char is "L"
            // 2) Ansci: String start char is ""
            // 3) byte array: String start char "{"
            // 
            if (ch == 'L') {
                start       = datum.indexOf('\"');
                end         = datum.lastIndexOf('\"');
                if ((start > end)           || 
                    (end   > datum.length())||
                    ((start == end) && (datum.length() > 0))) {
                    exceptionString = String.format("[FPD file error] The datum type of PCD %s in %s is VOID* and datum is "+
                                                    "a UNICODE string because start with L\", but format maybe"+
                                                    "is not right, correct UNICODE string is L\"...\"!",
                                                    cName,
                                                    moduleName);
                    return exceptionString;
                }

                strValue    = datum.substring(start + 1, end);
                if ((strValue.length() * 2) > maxDatumSize) {
                    exceptionString = String.format("[FPD file error] The datum type of PCD %s in %s is VOID*, and datum is "+
                                                    "a UNICODE string, but the datum size is %d exceed to <MaxDatumSize> : %d",
                                                    cName,
                                                    moduleName,
                                                    strValue.length() * 2, 
                                                    maxDatumSize);
                    return exceptionString;
                }
            } else if (ch == '\"'){
                start       = datum.indexOf('\"');
                end         = datum.lastIndexOf('\"');
                if ((start > end)           || 
                    (end   > datum.length())||
                    ((start == end) && (datum.length() > 0))) {
                    exceptionString = String.format("[FPD file error] The datum type of PCD %s in %s is VOID* and datum is "+
                                                    "a ANSCII string because start with \", but format maybe"+
                                                    "is not right, correct ANSIC string is \"...\"!",
                                                    cName,
                                                    moduleName);
                    return exceptionString;
                }
                strValue    = datum.substring(start + 1, end);
                if ((strValue.length()) > maxDatumSize) {
                    exceptionString = String.format("[FPD file error] The datum type of PCD %s in %s is VOID*, and datum is "+
                                                    "a ANSCI string, but the datum size is %d which exceed to <MaxDatumSize> : %d",
                                                    cName,
                                                    moduleName,
                                                    strValue.length(),
                                                    maxDatumSize);
                    return exceptionString;
                }
            } else if (ch =='{') {
                String[]  strValueArray;

                start           = datum.indexOf('{');
                end             = datum.lastIndexOf('}');
                strValue        = datum.substring(start + 1, end);
                strValue        = strValue.trim();
                if (strValue.length() == 0) {
                    break;
                }
                strValueArray   = strValue.split(",");
                for (index = 0; index < strValueArray.length; index ++) {
                    try{
                        value = Integer.decode(strValueArray[index]);
                    } catch (NumberFormatException nfeEx) {
                        exceptionString = String.format("[FPD file error] The datum type of PCD %s in %s is VOID*, and "+
                                                         "it is byte array in fact. For every byte in array should be a valid"+
                                                         "byte digital, but element %s is not a valid byte digital!",
                                                         cName,
                                                         moduleName,
                                                         strValueArray[index]);
                        return exceptionString;
                    }
                    if (value > 0xFF) {
                        exceptionString = String.format("[FPD file error] The datum type of PCD %s in %s is VOID*, "+
                                                        "it is byte array in fact. But the element of %s exceed the byte range",
                                                        cName,
                                                        moduleName,
                                                        strValueArray[index]);
                        return exceptionString;
                    }
                }

                if (strValueArray.length > maxDatumSize) {
                    exceptionString = String.format("[FPD file error] The datum type of PCD %s in %s is VOID*, and datum is byte"+
                                                    "array, but the number of bytes is %d which exceed to <MaxDatumSzie> : %d!",
                                                    cName,
                                                    moduleName,
                                                    strValueArray.length,
                                                    maxDatumSize);
                    return exceptionString;
                }
            } else {
                exceptionString = String.format("[FPD file error] The datum type of PCD %s in %s is VOID*. For VOID* type, you have three format choise:\n "+
                                                "1) UNICODE string: like L\"xxxx\";\r\n"+
                                                "2) ANSIC string: like \"xxx\";\r\n"+
                                                "3) Byte array: like {0x2, 0x45, 0x23}\r\n"+
                                                "But the datum in seems does not following above format!",
                                                cName, 
                                                moduleName);
                return exceptionString;
            }
            break;
        default:
            exceptionString = String.format("[FPD file error] For PCD entry %s in %s, datum type is unknown, it should be one of "+
                                            "UINT8, UINT16, UINT32, UINT64, VOID*, BOOLEAN",
                                            cName,
                                            moduleName);
            return exceptionString;
        }
        return null;
    }

    /**
       Get dynamic information for a dynamic PCD from <DynamicPcdBuildDefinition> seciton in FPD file.
       
       This function should be implemented in GlobalData in future.
       
       @param token         The token instance which has hold module's PCD information
       @param moduleName    The name of module who will use this Dynamic PCD.
       
       @return DynamicPcdBuildDefinitions.PcdBuildData
     */
    /***/
    private DynamicPcdBuildDefinitions.PcdBuildData getDynamicInfoFromFPD(Token     token,
                                                                          String    moduleName)
        throws EntityException {
        int    index             = 0;
        String exceptionString   = null;
        String dynamicPrimaryKey = null;
        DynamicPcdBuildDefinitions                    dynamicPcdBuildDefinitions = null;
        List<DynamicPcdBuildDefinitions.PcdBuildData> dynamicPcdBuildDataArray   = null;

        //
        // If FPD document is not be opened, open and initialize it.
        // 
        if (fpdDocInstance == null) {
            try {
                fpdDocInstance = (FrameworkPlatformDescriptionDocument)XmlObject.Factory.parse(new File(fpdFilePath));
            } catch(IOException ioE) {
                throw new EntityException("File IO error for xml file:" + fpdFilePath + "\n" + ioE.getMessage());
            } catch(XmlException xmlE) {
                throw new EntityException("Can't parse the FPD xml fle:" + fpdFilePath + "\n" + xmlE.getMessage());
            }
        }

        dynamicPcdBuildDefinitions = fpdDocInstance.getFrameworkPlatformDescription().getDynamicPcdBuildDefinitions();
        if (dynamicPcdBuildDefinitions == null) {
            exceptionString = String.format("[FPD file error] There are no <PcdDynamicBuildDescriptions> in FPD file but contains Dynamic type "+
                                            "PCD entry %s in module %s!",
                                            token.cName,
                                            moduleName);
            throw new EntityException(exceptionString);
        }

        dynamicPcdBuildDataArray = dynamicPcdBuildDefinitions.getPcdBuildDataList();
        for (index = 0; index < dynamicPcdBuildDataArray.size(); index ++) {
            //
            // Check <TokenSpaceGuid> is exist? In future, because all schema verification will tools
            // will check that, following checking code could be removed.
            // 
            if (dynamicPcdBuildDataArray.get(index).getTokenSpaceGuid() == null) {
                exceptionString = String.format("[FPD file error] There is no <TokenSpaceGuid> for PCD %s in <DynamicPcdBuildDefinitions>! This is required!",
                                                dynamicPcdBuildDataArray.get(index).getCName());
                throw new EntityException(exceptionString);
            }

            dynamicPrimaryKey = Token.getPrimaryKeyString(dynamicPcdBuildDataArray.get(index).getCName(),
                                                          translateSchemaStringToUUID(dynamicPcdBuildDataArray.get(index).getTokenSpaceGuid()));
            if (dynamicPrimaryKey.equalsIgnoreCase(token.getPrimaryKeyString())) {
                return dynamicPcdBuildDataArray.get(index);
            }
        }

        return null;
    }

    /**
       Update dynamic information for PCD entry.
       
       Dynamic information is retrieved from <PcdDynamicBuildDeclarations> in
       FPD file.
       
       @param moduleName        The name of the module who use this PCD
       @param token             The token instance
       @param datum             The <datum> in module's PCD information
       @param maxDatumSize      The <maxDatumSize> in module's PCD information
       
       @return Token
     */
    private Token updateDynamicInformation(String   moduleName, 
                                           Token    token,
                                           String   datum,
                                           int      maxDatumSize) 
        throws EntityException {
        int                 index           = 0;
        int                 offset;
        String              exceptionString = null;
        DynamicTokenValue   dynamicValue;
        SkuInstance         skuInstance     = null;
        String              temp;
        boolean             hasSkuId0       = false;

        List<DynamicPcdBuildDefinitions.PcdBuildData.SkuInfo>   skuInfoList = null;
        DynamicPcdBuildDefinitions.PcdBuildData                 dynamicInfo = null;

        dynamicInfo = getDynamicInfoFromFPD(token, moduleName);
        if (dynamicInfo == null) {
            exceptionString = String.format("[FPD file error] For Dynamic PCD %s used by module %s, "+
                                            "there is no dynamic information in <DynamicPcdBuildDefinitions> "+
                                            "in FPD file, but it is required!",
                                            token.cName,
                                            moduleName);
            throw new EntityException(exceptionString);
        }

        token.datumSize = dynamicInfo.getMaxDatumSize();

        exceptionString = verifyDatum(token.cName, 
                                      moduleName,
                                      null, 
                                      token.datumType, 
                                      token.datumSize);
        if (exceptionString != null) {
            throw new EntityException(exceptionString);
        }

        if ((maxDatumSize != 0) && 
            (maxDatumSize != token.datumSize)) {
            exceptionString = String.format("FPD file error] For dynamic PCD %s, the datum size in module %s is %d, but "+
                                            "the datum size in <DynamicPcdBuildDefinitions> is %d, they are not match!",
                                            token.cName,
                                            moduleName, 
                                            maxDatumSize,
                                            dynamicInfo.getMaxDatumSize());
            throw new EntityException(exceptionString);
        }

        skuInfoList = dynamicInfo.getSkuInfoList();

        //
        // Loop all sku data 
        // 
        for (index = 0; index < skuInfoList.size(); index ++) {
            skuInstance = new SkuInstance();
            //
            // Although SkuId in schema is BigInteger, but in fact, sku id is 32 bit value.
            // 
            temp = skuInfoList.get(index).getSkuId().toString();
            skuInstance.id = Integer.decode(temp);
            if (skuInstance.id == 0) {
                hasSkuId0 = true;
            }
            //
            // Judge whether is DefaultGroup at first, because most case is DefautlGroup.
            // 
            if (skuInfoList.get(index).getValue() != null) {
                skuInstance.value.setValue(skuInfoList.get(index).getValue());
                if ((exceptionString = verifyDatum(token.cName, 
                                                   null, 
                                                   skuInfoList.get(index).getValue(), 
                                                   token.datumType, 
                                                   token.datumSize)) != null) {
                    throw new EntityException(exceptionString);
                }

                token.skuData.add(skuInstance);

                //
                // Judege wether is same of datum between module's information
                // and dynamic information.
                // 
                if (datum != null) {
                    if ((skuInstance.id == 0)                                   &&
                        !datum.equalsIgnoreCase(skuInfoList.get(index).getValue())) {
                        exceptionString = "[FPD file error] For dynamic PCD " + token.cName + ", the value in module " + moduleName + " is " + datum.toString() + " but the "+
                                          "value of sku 0 data in <DynamicPcdBuildDefinition> is " + skuInstance.value.value + ". They are must be same!"+
                                          " or you could not define value for a dynamic PCD in every <ModuleSA>!"; 
                        throw new EntityException(exceptionString);
                    }
                }
                continue;
            }

            //
            // Judge whether is HII group case.
            // 
            if (skuInfoList.get(index).getVariableName() != null) {
                exceptionString = null;
                if (skuInfoList.get(index).getVariableGuid() == null) {
                    exceptionString = String.format("[FPD file error] For dynamic PCD %s in <DynamicPcdBuildDefinitions> section in FPD "+
                                                    "file, who use HII, but there is no <VariableGuid> defined for Sku %d data!",
                                                    token.cName,
                                                    index);
                                                    
                }

                if (skuInfoList.get(index).getVariableOffset() == null) {
                    exceptionString = String.format("[FPD file error] For dynamic PCD %s in <DynamicPcdBuildDefinitions> section in FPD "+
                                                    "file, who use HII, but there is no <VariableOffset> defined for Sku %d data!",
                                                    token.cName,
                                                    index);
                }

                if (skuInfoList.get(index).getHiiDefaultValue() == null) {
                    exceptionString = String.format("[FPD file error] For dynamic PCD %s in <DynamicPcdBuildDefinitions> section in FPD "+
                                                    "file, who use HII, but there is no <HiiDefaultValue> defined for Sku %d data!",
                                                    token.cName,
                                                    index);
                }

                if (exceptionString != null) {
                    throw new EntityException(exceptionString);
                }

                if ((exceptionString = verifyDatum(token.cName, 
                                                   null, 
                                                   skuInfoList.get(index).getHiiDefaultValue(), 
                                                   token.datumType, 
                                                   token.datumSize)) != null) {
                    throw new EntityException(exceptionString);
                }

                offset = Integer.decode(skuInfoList.get(index).getVariableOffset());
                if (offset > 0xFFFF) {
                    throw new EntityException(String.format("[FPD file error] For dynamic PCD %s ,  the variable offset defined in sku %d data "+
                                                            "exceed 64K, it is not allowed!",
                                                            token.cName,
                                                            index));
                }

                skuInstance.value.setHiiData(skuInfoList.get(index).getVariableName(),
                                             translateSchemaStringToUUID(skuInfoList.get(index).getVariableGuid().toString()),
                                             skuInfoList.get(index).getVariableOffset(),
                                             skuInfoList.get(index).getHiiDefaultValue());
                token.skuData.add(skuInstance);
                continue;
            }

            if (skuInfoList.get(index).getVpdOffset() != null) {
                skuInstance.value.setVpdData(skuInfoList.get(index).getVpdOffset());
                token.skuData.add(skuInstance);
                continue;
            }

            exceptionString = String.format("[FPD file error] For dynamic PCD %s, the dynamic info must "+
                                            "be one of 'DefaultGroup', 'HIIGroup', 'VpdGroup'.",
                                            token.cName);
            throw new EntityException(exceptionString);
        }

        if (!hasSkuId0) {
            exceptionString = String.format("[FPD file error] For dynamic PCD %s in <DynamicPcdBuildDefinitions>, there are "+
                                            "no sku id = 0 data, which is required for every dynamic PCD",
                                            token.cName);
            throw new EntityException(exceptionString);
        }

        return token;
    }

    /**
       Translate the schema string to UUID instance.
       
       In schema, the string of UUID is defined as following two types string:
        1) GuidArrayType: pattern = 0x[a-fA-F0-9]{1,8},( )*0x[a-fA-F0-9]{1,4},(
        )*0x[a-fA-F0-9]{1,4}(,( )*\{)?(,?( )*0x[a-fA-F0-9]{1,2}){8}( )*(\})?
       
        2) GuidNamingConvention: pattern =
        [a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}
       
       This function will convert string and create uuid instance.
       
       @param uuidString    UUID string in XML file
       
       @return UUID         UUID instance
    **/
    private UUID translateSchemaStringToUUID(String uuidString) 
        throws EntityException {
        String      temp;
        String[]    splitStringArray;
        int         index;
        int         chIndex;
        int         chLen;

        if (uuidString == null) {
            return null;
        }

        if (uuidString.length() == 0) {
            return null;
        }

        if (uuidString.equals("0") ||
            uuidString.equalsIgnoreCase("0x0")) {
            return new UUID(0, 0);
        }

        //
        // If the UUID schema string is GuidArrayType type then need translate 
        // to GuidNamingConvention type at first.
        // 
        if ((uuidString.charAt(0) == '0') && ((uuidString.charAt(1) == 'x') || (uuidString.charAt(1) == 'X'))) {
            splitStringArray = uuidString.split("," );
            if (splitStringArray.length != 11) {
                throw new EntityException ("[FPD file error] Wrong format for UUID string: " + uuidString);
            }

            //
            // Remove blank space from these string and remove header string "0x"
            // 
            for (index = 0; index < 11; index ++) {
                splitStringArray[index] = splitStringArray[index].trim();
                splitStringArray[index] = splitStringArray[index].substring(2, splitStringArray[index].length());
            }

            //
            // Add heading '0' to normalize the string length
            // 
            for (index = 3; index < 11; index ++) {
                chLen = splitStringArray[index].length();
                for (chIndex = 0; chIndex < 2 - chLen; chIndex ++) {
                    splitStringArray[index] = "0" + splitStringArray[index];
                }
            }

            //
            // construct the final GuidNamingConvention string
            // 
            temp = String.format("%s-%s-%s-%s%s-%s%s%s%s%s%s",
                                 splitStringArray[0],
                                 splitStringArray[1],
                                 splitStringArray[2],
                                 splitStringArray[3],
                                 splitStringArray[4],
                                 splitStringArray[5],
                                 splitStringArray[6],
                                 splitStringArray[7],
                                 splitStringArray[8],
                                 splitStringArray[9],
                                 splitStringArray[10]);
            uuidString = temp;
        }

        return UUID.fromString(uuidString);
    }

    /**
      check parameter for this action.
      
      @throws EntityException  Bad parameter.
    **/
    private void checkParameter() throws EntityException {
        File file = null;

        if((fpdFilePath    == null) ||(workspacePath  == null)) {
            throw new EntityException("WorkspacePath and FPDFileName should be blank for CollectPCDAtion!");
        }

        if(fpdFilePath.length() == 0 || workspacePath.length() == 0) {
            throw new EntityException("WorkspacePath and FPDFileName should be blank for CollectPCDAtion!");
        }

        file = new File(workspacePath);
        if(!file.exists()) {
            throw new EntityException("WorkpacePath " + workspacePath + " does not exist!");
        }

        file = new File(fpdFilePath);

        if(!file.exists()) {
            throw new EntityException("FPD File " + fpdFilePath + " does not exist!");
        }
    }

    /**
      Test case function

      @param argv  parameter from command line
    **/
    public static void main(String argv[]) throws EntityException {
        CollectPCDAction ca = new CollectPCDAction();
        ca.setWorkspacePath("m:/tianocore_latest/edk2");
        ca.setFPDFilePath("m:/tianocore_latest/edk2/EdkNt32Pkg/Nt32.fpd");
        ca.setActionMessageLevel(ActionMessage.MAX_MESSAGE_LEVEL);
        GlobalData.initInfo("Tools" + File.separator + "Conf" + File.separator + "FrameworkDatabase.db",
                            "m:/tianocore_latest/edk2");
        ca.execute();
    }
}
