package com.example.coverageanalyzer;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CoverageParser {
    
    // Data structure to store coverage information
    public static class CoverageInfo {
        public String className;
        public String simpleClassName;  // Just the class name without package
        public String fullClassName;    // Full class name with package
        public String sourceFileName;   // Source file name
        public double instructionCoverage = 0.0;
        public double branchCoverage = 0.0;
        public double complexityCoverage = 0.0;
        public double lineCoverage = 0.0;
        public double methodCoverage = 0.0;
        public double classCoverage = 0.0;
        
        // Raw counter values for detailed reporting
        public int instructionCovered = 0;
        public int instructionMissed = 0;
        public int branchCovered = 0;
        public int branchMissed = 0;
        public int lineCovered = 0;
        public int lineMissed = 0;
        public int methodCovered = 0;
        public int methodMissed = 0;
        public int classCovered = 0;
        public int classMissed = 0;
        public int complexityCovered = 0;
        public int complexityMissed = 0;
        
        public CoverageInfo(String fullClassName) {
            this.fullClassName = fullClassName;
            this.className = fullClassName;
            // Extract simple class name (without package)
            int lastDotIndex = fullClassName.lastIndexOf('.');
            if (lastDotIndex >= 0) {
                this.simpleClassName = fullClassName.substring(lastDotIndex + 1);
            } else {
                this.simpleClassName = fullClassName;
            }
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Class: ").append(simpleClassName).append("\n");
            sb.append("Source File: ").append(sourceFileName).append("\n");
            sb.append("Instruction Coverage: ").append(String.format("%.2f%%", instructionCoverage * 100)).append("\n");
            sb.append("Branch Coverage: ").append(String.format("%.2f%%", branchCoverage * 100)).append("\n");
            sb.append("Line Coverage: ").append(String.format("%.2f%%", lineCoverage * 100)).append("\n");
            sb.append("Method Coverage: ").append(String.format("%.2f%%", methodCoverage * 100)).append("\n");
            sb.append("Class Coverage: ").append(String.format("%.2f%%", classCoverage * 100)).append("\n");
            return sb.toString();
        }
    }
    
    public Map<String, CoverageInfo> parseReport(String reportPath) {
        Map<String, CoverageInfo> coverageMap = new HashMap<>();
        
        try {
            // Handle both file and directory paths
            File reportFileOrDir = new File(reportPath);
            File xmlReport = null;
            
            if (reportFileOrDir.isFile() && reportFileOrDir.getName().toLowerCase().endsWith(".xml")) {
                // If the path is directly to an XML file (including jacoco.xml)
                xmlReport = reportFileOrDir;
            } else if (reportFileOrDir.isDirectory()) {
                // If the path is a directory, look for jacoco.xml
                xmlReport = findJacocoXml(reportFileOrDir);
            } else if (reportFileOrDir.exists()) {
                // If it's neither file nor dir, try to get parent
                File parentDir = reportFileOrDir.getParentFile();
                if (parentDir != null) {
                    xmlReport = findJacocoXml(parentDir);
                }
            }
            
            if (xmlReport != null) {
                coverageMap = parseJacocoXml(xmlReport);
            } else {
                System.out.println("Could not find an XML coverage report in the specified path.");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return coverageMap;
    }
    
    // Helper method to find jacoco.xml in a directory
    private File findJacocoXml(File directory) {
        // Look for jacoco.xml file in directory
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().equalsIgnoreCase("jacoco.xml")) {
                    return file;
                }
            }
            
            // If jacoco.xml is not found, try looking in subdirectories
            for (File file : files) {
                if (file.isDirectory()) {
                    File[] subFiles = file.listFiles();
                    if (subFiles != null) {
                        for (File subFile : subFiles) {
                            if (subFile.getName().equalsIgnoreCase("jacoco.xml")) {
                                return subFile;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private Map<String, CoverageInfo> parseJacocoXml(File xmlFile) throws Exception {
        Map<String, CoverageInfo> coverageMap = new HashMap<>();
        
        SAXReader reader = new SAXReader();
        Document document = reader.read(xmlFile);
        
        Element root = document.getRootElement();
        if ("report".equals(root.getName())) {
            List<Element> packageElements = root.elements("package");
            
            for (Element pkgElement : packageElements) {
                String packageName = pkgElement.attributeValue("name");
                List<Element> classElements = pkgElement.elements("class");
                
                for (Element classElement : classElements) {
                    String className = classElement.attributeValue("name");
                    String sourceFileName = classElement.attributeValue("sourcefilename");
                    String fullClassName = packageName.isEmpty() ? 
                        className.substring(className.lastIndexOf('/') + 1) : 
                        packageName.replace('/', '.') + "." + className.substring(className.lastIndexOf('/') + 1);
                    
                    // Filter out interfaces with 0% coverage - these are typically pure interfaces
                    // that don't have executable code
                    if (isPureInterfaceWithNoCoverage(classElement)) {
                        continue; // Skip pure interfaces with no executable code
                    }
                    
                    CoverageInfo info = new CoverageInfo(fullClassName);
                    info.sourceFileName = sourceFileName; // Store source file name
                    
                    // Parse counter information
                    List<Element> counterElements = classElement.elements("counter");
                    for (Element counterElement : counterElements) {
                        String type = counterElement.attributeValue("type");
                        int covered = Integer.parseInt(counterElement.attributeValue("covered", "0"));
                        int missed = Integer.parseInt(counterElement.attributeValue("missed", "0"));
                        
                        double total = covered + missed;
                        double ratio = total > 0 ? covered / total : 0.0;
                        
                        switch (type) {
                            case "INSTRUCTION":
                                info.instructionCoverage = ratio;
                                info.instructionCovered = covered;
                                info.instructionMissed = missed;
                                break;
                            case "BRANCH":
                                info.branchCoverage = ratio;
                                info.branchCovered = covered;
                                info.branchMissed = missed;
                                break;
                            case "LINE":
                                info.lineCoverage = ratio;
                                info.lineCovered = covered;
                                info.lineMissed = missed;
                                break;
                            case "COMPLEXITY":
                                info.complexityCoverage = ratio;
                                info.complexityCovered = covered;
                                info.complexityMissed = missed;
                                break;
                            case "METHOD":
                                info.methodCoverage = ratio;
                                info.methodCovered = covered;
                                info.methodMissed = missed;
                                break;
                            case "CLASS":
                                info.classCoverage = ratio;
                                info.classCovered = covered;
                                info.classMissed = missed;
                                break;
                        }
                    }
                    
                    coverageMap.put(info.simpleClassName, info);  // Use simple class name as key
                }
            }
        }
        
        return coverageMap;
    }
    
    // Helper method to identify pure interfaces with no executable code
    private boolean isPureInterfaceWithNoCoverage(Element classElement) {
        // Check if this class has 0 instructions covered and 0 missed
        // which typically indicates a pure interface with no executable code
        List<Element> counterElements = classElement.elements("counter");
        int instructionCovered = 0;
        int instructionMissed = 0;
        
        for (Element counterElement : counterElements) {
            String type = counterElement.attributeValue("type");
            if ("INSTRUCTION".equals(type)) {
                instructionCovered = Integer.parseInt(counterElement.attributeValue("covered", "0"));
                instructionMissed = Integer.parseInt(counterElement.attributeValue("missed", "0"));
                break;
            }
        }
        
        // If there are no instructions at all (both covered and missed are 0),
        // this likely indicates a pure interface with no executable code
        return instructionCovered == 0 && instructionMissed == 0;
    }
    
    // Helper method to identify DAO classes by simple name
    private boolean isDaoClass(String simpleClassName) {
        String lowerName = simpleClassName.toLowerCase();
        return lowerName.endsWith("dao") || 
               lowerName.endsWith("mapper");
    }
    
    public CoverageInfo getClassCoverage(Map<String, CoverageInfo> coverageMap, String className) {
        // First try exact match with simple class name
        CoverageInfo exactMatch = coverageMap.get(className);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // Then try case-insensitive partial match
        for (Map.Entry<String, CoverageInfo> entry : coverageMap.entrySet()) {
            if (entry.getKey().toLowerCase().contains(className.toLowerCase())) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    public List<CoverageInfo> getRelatedClassCoverages(Map<String, CoverageInfo> coverageMap, String className) {
        // Find all classes that contain the given class name (case-insensitive)
        // But exclude pure interfaces with no executable code
        return coverageMap.entrySet().stream()
            .filter(entry -> entry.getKey().toLowerCase().contains(className.toLowerCase()))
            .filter(entry -> !isDaoClass(entry.getKey())) // Exclude DAO classes
            .filter(entry -> !(entry.getValue().instructionCoverage == 0.0 && 
                              entry.getValue().lineCoverage == 0.0 &&
                              entry.getValue().methodCoverage == 0.0)) // Exclude pure interfaces
            .map(entry -> entry.getValue())
            .collect(Collectors.toList());
    }
    
    // Method to find source file for a given class, using parent directory of report as source root
    public File findSourceFile(String reportPath, CoverageInfo info) {
        if (info.sourceFileName == null || info.sourceFileName.isEmpty()) {
            return null;
        }
        
        // Handle different path formats
        File reportFile = new File(reportPath);
        String basePath;
        
        if (reportFile.isDirectory()) {
            // If reportPath is a directory, use it as base
            basePath = reportFile.getAbsolutePath();
        } else {
            // If reportPath is a file, use its parent directory
            basePath = reportFile.getParent();
        }
        
        if (basePath == null) return null;
        
        // Convert package name to directory path
        String packageDir = info.fullClassName.substring(0, info.fullClassName.lastIndexOf('.')).replace('.', '/');
        
        // Try different common source directory structures relative to the base path
        String[] sourcePaths = {
            basePath + "/../../src/main/java/" + packageDir + "/" + info.sourceFileName,  // Go up two levels from report dir
            basePath + "/../src/main/java/" + packageDir + "/" + info.sourceFileName,    // Go up one level from report dir
            basePath + "/src/main/java/" + packageDir + "/" + info.sourceFileName,       // Direct src in base
            basePath + "/../../src/test/java/" + packageDir + "/" + info.sourceFileName,
            basePath + "/../src/test/java/" + packageDir + "/" + info.sourceFileName,
            basePath + "/src/test/java/" + packageDir + "/" + info.sourceFileName,
            basePath + "/../../src/java/" + packageDir + "/" + info.sourceFileName,
            basePath + "/../src/java/" + packageDir + "/" + info.sourceFileName,
            basePath + "/src/java/" + packageDir + "/" + info.sourceFileName,
            basePath + "/" + packageDir + "/" + info.sourceFileName,  // Try package path directly
            basePath + "/" + info.sourceFileName  // Try just filename in base
        };
        
        for (String path : sourcePaths) {
            File sourceFile = new File(path);
            if (sourceFile.exists()) {
                return sourceFile;
            }
        }
        
        // Additional fallback: if we know the project name from the path, try to construct path
        String projectPath = findProjectRoot(basePath);
        if (projectPath != null) {
            String[] altPaths = {
                projectPath + "/src/main/java/" + packageDir + "/" + info.sourceFileName,
                projectPath + "/src/test/java/" + packageDir + "/" + info.sourceFileName
            };
            
            for (String path : altPaths) {
                File sourceFile = new File(path);
                if (sourceFile.exists()) {
                    return sourceFile;
                }
            }
        }
        
        return null;
    }
    
    // Helper method to try to find project root
    private String findProjectRoot(String path) {
        File currentDir = new File(path);
        
        // Go up the directory tree looking for common project markers
        for (int i = 0; i < 5; i++) {  // Don't go too deep
            if (new File(currentDir, "pom.xml").exists() ||
                new File(currentDir, "build.gradle").exists() ||
                new File(currentDir, "settings.gradle").exists() ||
                new File(currentDir, "package.json").exists()) {
                return currentDir.getAbsolutePath();
            }
            
            File parent = currentDir.getParentFile();
            if (parent == null) break;
            currentDir = parent;
        }
        
        return null;
    }
    
    // Method to read source file lines
    public List<String> readSourceFileLines(File sourceFile) throws Exception {
        return Files.readAllLines(Paths.get(sourceFile.getAbsolutePath()));
    }
}