package com.example.coverageanalyzer;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class SimpleCoverageAnalyzer {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -cp target/coverage-analyzer-1.0.0.jar com.example.coverageanalyzer.SimpleCoverageAnalyzer <jacoco_report_path>");
            System.out.println("Simple Coverage Analyzer - Generates Excel report from JaCoCo XML report");
            return;
        }
        
        String reportPath = args[0];
        
        CoverageParser coverageParser = new CoverageParser();
        
        try {
            Map<String, CoverageParser.CoverageInfo> coverageMap = coverageParser.parseReport(reportPath);
            
            // Create workbook
            Workbook workbook = new XSSFWorkbook();
            
            // Create summary sheet
            Sheet summarySheet = workbook.createSheet("Summary");
            
            // Create header row
            Row headerRow = summarySheet.createRow(0);
            headerRow.createCell(0).setCellValue("Class Name");
            headerRow.createCell(1).setCellValue("Instruction Coverage");
            headerRow.createCell(2).setCellValue("Branch Coverage");
            headerRow.createCell(3).setCellValue("Line Coverage");
            headerRow.createCell(4).setCellValue("Method Coverage");
            
            int rowNum = 1;
            for (Map.Entry<String, CoverageParser.CoverageInfo> entry : coverageMap.entrySet()) {
                CoverageParser.CoverageInfo info = entry.getValue();
                
                // Only include classes with less than 100% instruction coverage
                if (info.instructionCoverage < 1.0) {
                    Row row = summarySheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(info.simpleClassName);
                    row.createCell(1).setCellValue(info.instructionCoverage * 100);
                    row.createCell(2).setCellValue(info.branchCoverage * 100);
                    row.createCell(3).setCellValue(info.lineCoverage * 100);
                    row.createCell(4).setCellValue(info.methodCoverage * 100);
                    
                    System.out.println("Processing class: " + info.simpleClassName + 
                                      " (Coverage: " + (info.instructionCoverage * 100) + "%)");
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < 5; i++) {
                summarySheet.autoSizeColumn(i);
            }
            
            // Create detailed sheets for classes with less than 100% coverage
            for (Map.Entry<String, CoverageParser.CoverageInfo> entry : coverageMap.entrySet()) {
                CoverageParser.CoverageInfo info = entry.getValue();
                
                if (info.instructionCoverage < 1.0) {
                    String sheetName = info.simpleClassName.length() > 30 ? 
                        info.simpleClassName.substring(0, 30) : info.simpleClassName;
                    XSSFSheet detailSheet = (XSSFSheet) workbook.createSheet(sheetName);
                    
                    // Create header row for details
                    Row detailHeaderRow = detailSheet.createRow(0);
                    detailHeaderRow.createCell(0).setCellValue("Source Code");
                    
                    // Find and read the source file for this class
                    File sourceFile = coverageParser.findSourceFile(reportPath, info);
                    if (sourceFile != null && sourceFile.exists()) {
                        try {
                            List<String> sourceLines = coverageParser.readSourceFileLines(sourceFile);
                            
                            int detailRowNum = 1;
                            for (int i = 0; i < sourceLines.size(); i++) {
                                Row sourceRow = detailSheet.createRow(detailRowNum++);
                                sourceRow.createCell(0).setCellValue(sourceLines.get(i));  // Source code
                            }
                            
                            System.out.println("  Added " + sourceLines.size() + " lines of source code for " + info.simpleClassName);
                        } catch (Exception e) {
                            Row errorRow = detailSheet.createRow(1);
                            errorRow.createCell(0).setCellValue("Error reading source file: " + e.getMessage());
                        }
                    } else {
                        Row errorRow = detailSheet.createRow(1);
                        errorRow.createCell(0).setCellValue("Source file not found: " + info.sourceFileName + " for class " + info.fullClassName);
                        
                        System.out.println("  Warning: Could not find source file for " + info.simpleClassName);
                    }
                    
                    // Auto-size columns for detail sheet
                    detailSheet.autoSizeColumn(0);
                }
            }
            
            // Save the workbook
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "CoverageAnalysis_" + timestamp + ".xlsx";
            FileOutputStream outputStream = new FileOutputStream(fileName);
            workbook.write(outputStream);
            workbook.close();
            
            System.out.println("\nExport completed successfully: " + fileName);
            System.out.println("The Excel file contains:");
            System.out.println("- Summary sheet with class-level coverage statistics");
            System.out.println("- Detailed sheets for each class with less than 100% coverage");
            System.out.println("  showing the full source code for those classes");
            
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
