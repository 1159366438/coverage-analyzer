package com.example.coverageanalyzer;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainApp extends Application {
    
    private TextField reportPathField;
    private TextField searchField;
    private TextArea outputArea;
    private CoverageParser coverageParser;
    
    @Override
    public void start(Stage primaryStage) {
        coverageParser = new CoverageParser();
        
        // Create UI components
        reportPathField = new TextField();
        reportPathField.setPromptText("JaCoCo XML Report Path");
        
        Button browseReportButton = new Button("Browse Report");
        browseReportButton.setOnAction(e -> browseReport(primaryStage));
        
        Button browseFileButton = new Button("Browse File");
        browseFileButton.setOnAction(e -> browseReportFile(primaryStage));
        
        searchField = new TextField();
        searchField.setPromptText("Enter class name to search");
        
        Button searchButton = new Button("Search");
        searchButton.setOnAction(e -> searchClasses());
        
        Button exportButton = new Button("Export to Excel");
        exportButton.setOnAction(e -> exportToExcel());
        
        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearOutput());
        
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        
        // Layout
        VBox topBox = new VBox(5);
        topBox.setPadding(new Insets(10));
        
        HBox reportBox = new HBox(5);
        reportBox.getChildren().addAll(new Label("Report Path:"), reportPathField, browseReportButton, browseFileButton);
        
        HBox searchBox = new HBox(5);
        searchBox.getChildren().addAll(new Label("Search:"), searchField, searchButton);
        
        HBox buttonBox = new HBox(5);
        buttonBox.getChildren().addAll(exportButton, clearButton);
        
        topBox.getChildren().addAll(reportBox, searchBox, buttonBox);
        
        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(outputArea);
        
        Scene scene = new Scene(root, 800, 600);
        
        primaryStage.setTitle("JaCoCo Coverage Analyzer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    private void browseReport(Stage stage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select JaCoCo Report Directory");
        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            reportPathField.setText(selectedDir.getAbsolutePath());
        }
    }
    
    private void browseReportFile(Stage stage) {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select JaCoCo XML Report File");
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("XML Files", "*.xml")
        );
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            reportPathField.setText(selectedFile.getAbsolutePath());
        }
    }
    
    private void searchClasses() {
        String reportPath = reportPathField.getText().trim();
        String className = searchField.getText().trim();
        
        if (reportPath.isEmpty()) {
            showAlert("Please select a JaCoCo report directory.");
            return;
        }
        
        if (className.isEmpty()) {
            showAlert("Please enter a class name to search.");
            return;
        }
        
        try {
            Map<String, CoverageParser.CoverageInfo> coverageMap = coverageParser.parseReport(reportPath);
            List<CoverageParser.CoverageInfo> relatedClasses = coverageParser.getRelatedClassCoverages(coverageMap, className);
            
            if (relatedClasses.isEmpty()) {
                outputArea.setText("No classes found containing '" + className + "'");
            } else {
                StringBuilder sb = new StringBuilder();
                for (CoverageParser.CoverageInfo info : relatedClasses) {
                    sb.append(info.toString()).append("\n").append("---\n");
                }
                outputArea.setText(sb.toString());
            }
        } catch (Exception e) {
            showAlert("Error processing report: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    private void exportToExcel() {
        String reportPath = reportPathField.getText();
        String className = searchField.getText().trim(); // Get the class name from search field
        if (reportPath.isEmpty()) {
            showAlert("Please select a JaCoCo XML report file.");
            return;
        }

        try {
            // Parse the coverage report
            Map<String, CoverageParser.CoverageInfo> coverageMap = coverageParser.parseReport(reportPath);

            if (coverageMap.isEmpty()) {
                outputArea.appendText("No coverage data found in the report.\n");
                return;
            }

            // Filter classes based on search term if provided
            Map<String, CoverageParser.CoverageInfo> filteredCoverageMap;
            if (!className.isEmpty()) {
                // Get related classes based on search term
                List<CoverageParser.CoverageInfo> relatedClasses = coverageParser.getRelatedClassCoverages(coverageMap, className);
                filteredCoverageMap = new HashMap<>();
                for (CoverageParser.CoverageInfo info : relatedClasses) {
                    filteredCoverageMap.put(info.simpleClassName, info);
                }
                
                if (filteredCoverageMap.isEmpty()) {
                    showAlert("No classes found containing '" + className + "'.");
                    return;
                }
            } else {
                // If no search term, use all classes
                filteredCoverageMap = coverageMap;
            }

            // Create workbook
            Workbook workbook = new XSSFWorkbook();

            // Create summary sheet
            Sheet summarySheet = workbook.createSheet("Summary");

            // Create header row with Element, Missed Instructions Count, and Coverage % columns
            Row headerRow = summarySheet.createRow(0);
            headerRow.createCell(0).setCellValue("Element");
            headerRow.createCell(1).setCellValue("Missed Instructions Count");
            headerRow.createCell(2).setCellValue("Coverage %");

            int rowNum = 1;
            for (Map.Entry<String, CoverageParser.CoverageInfo> entry : filteredCoverageMap.entrySet()) {
                CoverageParser.CoverageInfo info = entry.getValue();

                // Get missed instructions count and coverage percentage directly from parsed data
                int missedInstructions = info.instructionMissed;
                double coveragePercentage = info.instructionCoverage * 100;

                // Include filtered classes in the summary with required columns
                Row row = summarySheet.createRow(rowNum++);
                row.createCell(0).setCellValue(info.simpleClassName); // Element column
                row.createCell(1).setCellValue(missedInstructions); // Missed Instructions Count column
                row.createCell(2).setCellValue(String.format("%.2f%%", coveragePercentage)); // Coverage % column with % sign
            }

            // Auto-size columns
            for (int i = 0; i < 3; i++) {
                summarySheet.autoSizeColumn(i);
            }

            // Create individual sheets for each class with less than 100% coverage
            for (Map.Entry<String, CoverageParser.CoverageInfo> entry : filteredCoverageMap.entrySet()) {
                CoverageParser.CoverageInfo info = entry.getValue();

                // Only create detailed sheet if there are missed instructions (coverage < 100%)
                if (info.instructionCoverage < 1.0) { // Less than 100% coverage
                    String sheetName = info.simpleClassName.length() > 30 ? 
                        info.simpleClassName.substring(0, 30) : info.simpleClassName;
                    XSSFSheet detailSheet = (XSSFSheet) workbook.createSheet(sheetName);

                    // Create header row for details
                    Row detailHeaderRow = detailSheet.createRow(0);
                    detailHeaderRow.createCell(0).setCellValue("Source Code");
                    detailHeaderRow.createCell(1).setCellValue("Instruction Coverage %");

                    // Find and read the source file for this class
                    File sourceFile = coverageParser.findSourceFile(reportPath, info);
                    if (sourceFile != null && sourceFile.exists()) {
                        try {
                            List<String> sourceLines = coverageParser.readSourceFileLines(sourceFile);

                            int detailRowNum = 1;
                            for (int i = 0; i < sourceLines.size(); i++) {
                                Row sourceRow = detailSheet.createRow(detailRowNum++);
                                sourceRow.createCell(0).setCellValue(sourceLines.get(i));  // Source code
                                sourceRow.createCell(1).setCellValue(info.instructionCoverage * 100); // Coverage %
                            }
                        } catch (Exception e) {
                            Row errorRow = detailSheet.createRow(1);
                            errorRow.createCell(0).setCellValue("Error reading source file: " + e.getMessage());
                            errorRow.createCell(1).setCellValue(info.instructionCoverage * 100);
                        }
                    } else {
                        Row errorRow = detailSheet.createRow(1);
                        errorRow.createCell(0).setCellValue("Source file not found: " + info.sourceFileName + " for class " + info.fullClassName);
                        errorRow.createCell(1).setCellValue(info.instructionCoverage * 100);
                    }

                    // Auto-size columns for detail sheet
                    detailSheet.autoSizeColumn(0);
                    if (detailSheet.getRow(0) != null) {
                        detailSheet.autoSizeColumn(1);
                    }
                }
            }

            // Show save dialog to let user choose where to save the file
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Save Coverage Analysis Excel File");
            fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
            );
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            fileChooser.setInitialFileName("CoverageAnalysis_" + timestamp + ".xlsx");
            
            // Get the primary stage from the application to use as owner for the file chooser
            // We'll use the outputArea to get the window since it's part of the scene
            Stage ownerStage = (Stage) outputArea.getScene().getWindow();
            File selectedFile = fileChooser.showSaveDialog(ownerStage);
            
            if (selectedFile != null) {
                try (FileOutputStream outputStream = new FileOutputStream(selectedFile)) {
                    workbook.write(outputStream);
                    outputArea.appendText("\nExported to: " + selectedFile.getAbsolutePath());
                    showAlert("Export completed successfully: " + selectedFile.getAbsolutePath());
                } finally {
                    // Ensure workbook is closed even if there's an error writing to file
                    try {
                        workbook.close();
                    } catch (IOException e) {
                        System.err.println("Error closing workbook: " + e.getMessage());
                    }
                }
            } else {
                // User cancelled the save operation
                outputArea.appendText("\nExport cancelled by user.");
                // Close workbook if user cancels the save dialog
                workbook.close();
            }

        } catch (IOException e) {
            showAlert("Error exporting to Excel: " + e.getMessage());
            e.printStackTrace();
        }
    }
private void clearOutput() {
        outputArea.clear();
    }
    
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // Helper method to get missed instructions based on the coverage report
    private int calculateMissedInstructions(String reportPath, CoverageParser.CoverageInfo info) {
        // Return the actual missed instructions count that was parsed and stored
        return info.instructionMissed;
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}