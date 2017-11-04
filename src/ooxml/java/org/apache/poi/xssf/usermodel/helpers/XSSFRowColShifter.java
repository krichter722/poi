/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.xssf.usermodel.helpers;

import org.apache.poi.ss.formula.*;
import org.apache.poi.ss.formula.ptg.Ptg;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.helpers.BaseRowColShifter;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Internal;
import org.apache.poi.util.POILogFactory;
import org.apache.poi.util.POILogger;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCfRule;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTConditionalFormatting;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTWorksheet;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for code common to {@link XSSFRowShifter} and {@link XSSFColumnShifter}
 */
@Internal
/*private*/ final class XSSFRowColShifter extends BaseRowColShifter {
    private static final POILogger logger = POILogFactory.getLogger(XSSFRowColShifter.class);

    private XSSFRowColShifter() { /*no instances for static classes*/}

    /**
     * Updated named ranges
     */
    /*package*/ static void updateNamedRanges(Sheet sheet, FormulaShifter formulaShifter) {
        Workbook wb = sheet.getWorkbook();
        XSSFEvaluationWorkbook fpb = XSSFEvaluationWorkbook.create((XSSFWorkbook) wb);
        for (Name name : wb.getAllNames()) {
            String formula = name.getRefersToFormula();
            int sheetIndex = name.getSheetIndex();
            final int rowIndex = -1; //don't care, named ranges are not allowed to include structured references

            Ptg[] ptgs = FormulaParser.parse(formula, fpb, FormulaType.NAMEDRANGE, sheetIndex, rowIndex);
            if (formulaShifter.adjustFormula(ptgs, sheetIndex)) {
                String shiftedFmla = FormulaRenderer.toFormulaString(fpb, ptgs);
                name.setRefersToFormula(shiftedFmla);
            }
        }
    }

    /*package*/ static void updateConditionalFormatting(Sheet sheet, FormulaShifter formulaShifter) {
        XSSFSheet xsheet = (XSSFSheet) sheet;
        XSSFWorkbook wb = xsheet.getWorkbook();
        int sheetIndex = wb.getSheetIndex(sheet);
        final int rowIndex = -1; //don't care, structured references not allowed in conditional formatting

        XSSFEvaluationWorkbook fpb = XSSFEvaluationWorkbook.create(wb);
        CTWorksheet ctWorksheet = xsheet.getCTWorksheet();
        CTConditionalFormatting[] conditionalFormattingArray = ctWorksheet.getConditionalFormattingArray();
        // iterate backwards due to possible calls to ctWorksheet.removeConditionalFormatting(j)
        for (int j = conditionalFormattingArray.length - 1; j >= 0; j--) {
            CTConditionalFormatting cf = conditionalFormattingArray[j];

            ArrayList<CellRangeAddress> cellRanges = new ArrayList<>();
            for (Object stRef : cf.getSqref()) {
                String[] regions = stRef.toString().split(" ");
                for (String region : regions) {
                    cellRanges.add(CellRangeAddress.valueOf(region));
                }
            }

            boolean changed = false;
            List<CellRangeAddress> temp = new ArrayList<>();
            for (CellRangeAddress craOld : cellRanges) {
                CellRangeAddress craNew = shiftRange(formulaShifter, craOld, sheetIndex);
                if (craNew == null) {
                    changed = true;
                    continue;
                }
                temp.add(craNew);
                if (craNew != craOld) {
                    changed = true;
                }
            }

            if (changed) {
                int nRanges = temp.size();
                if (nRanges == 0) {
                    ctWorksheet.removeConditionalFormatting(j);
                    continue;
                }
                List<String> refs = new ArrayList<>();
                for(CellRangeAddress a : temp) refs.add(a.formatAsString());
                cf.setSqref(refs);
            }

            for(CTCfRule cfRule : cf.getCfRuleArray()){
                String[] formulaArray = cfRule.getFormulaArray();
                for (int i = 0; i < formulaArray.length; i++) {
                    String formula = formulaArray[i];
                    Ptg[] ptgs = FormulaParser.parse(formula, fpb, FormulaType.CELL, sheetIndex, rowIndex);
                    if (formulaShifter.adjustFormula(ptgs, sheetIndex)) {
                        String shiftedFmla = FormulaRenderer.toFormulaString(fpb, ptgs);
                        cfRule.setFormulaArray(i, shiftedFmla);
                    }
                }
            }
        }
    }


    /*package*/ static void updateHyperlinks(Sheet sheet, FormulaShifter formulaShifter) {
        int sheetIndex = sheet.getWorkbook().getSheetIndex(sheet);
        List<? extends Hyperlink> hyperlinkList = sheet.getHyperlinkList();

        for (Hyperlink hyperlink : hyperlinkList) {
            XSSFHyperlink xhyperlink = (XSSFHyperlink) hyperlink;
            String cellRef = xhyperlink.getCellRef();
            CellRangeAddress cra = CellRangeAddress.valueOf(cellRef);
            CellRangeAddress shiftedRange = shiftRange(formulaShifter, cra, sheetIndex);
            if (shiftedRange != null && shiftedRange != cra) {
                // shiftedRange should not be null. If shiftedRange is null, that means
                // that a hyperlink wasn't deleted at the beginning of shiftRows when
                // identifying rows that should be removed because they will be overwritten
                xhyperlink.setCellReference(shiftedRange.formatAsString());
            }
        }
    }


}
