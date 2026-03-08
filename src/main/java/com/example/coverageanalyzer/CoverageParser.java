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
        // Look for jacoco.xml file in directory and its subdirectories
        return findFileRecursively(directory, "jacoco.xml");
    }
    
    /**
     * Recursively finds a file with the given name in the directory tree starting from baseDir
     */
    private File findFileRecursively(File baseDir, String fileName) {
        return findFileRecursively(baseDir, fileName, true); // Default to case-insensitive
    }
    
    /**
     * Recursively finds a file with the given name in the directory tree starting from baseDir
     * @param baseDir The directory to search in
     * @param fileName The name of the file to find
     * @param caseInsensitive Whether to perform case-insensitive matching
     */
    private File findFileRecursively(File baseDir, String fileName, boolean caseInsensitive) {
        if (baseDir == null || !baseDir.isDirectory()) {
            return null;
        }
        
        File[] files = baseDir.listFiles();
        if (files == null) return null;
        
        for (File file : files) {
            boolean matches;
            if (caseInsensitive) {
                matches = file.getName().equalsIgnoreCase(fileName);
            } else {
                matches = file.getName().equals(fileName);
            }
            
            if (matches && file.isFile()) {
                return file;
            }
            
            if (file.isDirectory()) {
                File found = findFileRecursively(file, fileName, caseInsensitive);
                if (found != null) {
                    return found;
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
    
    // Helper method to get counter values by type
    private int[] getCounterValues(Element classElement, String type) {
        List<Element> counterElements = classElement.elements("counter");
        int covered = 0;
        int missed = 0;
        
        for (Element counterElement : counterElements) {
            if (type.equals(counterElement.attributeValue("type"))) {
                covered = Integer.parseInt(counterElement.attributeValue("covered", "0"));
                missed = Integer.parseInt(counterElement.attributeValue("missed", "0"));
                break;
            }
        }
        
        return new int[]{covered, missed};
    }
    
    // Helper method to identify pure interfaces with no executable code
    private boolean isPureInterfaceWithNoCoverage(Element classElement) {
        // Check if this class has 0 instructions covered and 0 missed
        // which typically indicates a pure interface with no executable code
        int[] instructionValues = getCounterValues(classElement, "INSTRUCTION");
        int instructionCovered = instructionValues[0];
        int instructionMissed = instructionValues[1];
        
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
            .filter(entry -> !(entry.getValue().instructionCovered == 0 && 
                              entry.getValue().instructionMissed == 0)) // Exclude classes with no executable code at all
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
        String sourceSearchDir;
        
        if (reportFile.isDirectory()) {
            // If reportPath is a directory, look for source files in the same directory
            // For the path like D:\IdeaProjects\attendance-system\backend\target\site\jacoco
            // We now search directly in D:\IdeaProjects\attendance-system\backend\target\site\jacoco
            sourceSearchDir = reportFile.getAbsolutePath();
        } else {
            // If reportPath is a file (like jacoco.xml), use its parent directory
            String parentPath = reportFile.getParent();
            if (parentPath != null) {
                sourceSearchDir = parentPath;
            } else {
                return null;
            }
        }
        
        if (sourceSearchDir == null) return null;
        
        // Convert package name to directory path
        String packageDir = info.fullClassName.substring(0, info.fullClassName.lastIndexOf('.')).replace('.', '/');
        
        // First, try to find the regular .java source file
        File baseDir = new File(sourceSearchDir);
        File sourceFile = searchForSourceFileInDir(baseDir, packageDir, info.sourceFileName);
        if (sourceFile != null) {
            return sourceFile;
        }
        
        // If not found, try to find the .java.html file (JaCoCo HTML report format)
        String htmlSourceFileName = info.sourceFileName + ".html";
        sourceFile = searchForSourceFileInDir(baseDir, packageDir, htmlSourceFileName);
        if (sourceFile != null) {
            return sourceFile;
        }
        
        return null;
    }
    
    /**
     * Searches for the source file in the specified directory
     * following the package structure
     */
    private File searchForSourceFileInDir(File baseDir, String packageDir, String sourceFileName) {
        if (baseDir == null || !baseDir.exists() || !baseDir.isDirectory()) {
            return null;
        }
        
        // Look for the source file in the expected package directory structure
        File expectedPackageDir = new File(baseDir, packageDir);
        if (expectedPackageDir.exists() && expectedPackageDir.isDirectory()) {
            File sourceFile = new File(expectedPackageDir, sourceFileName);
            if (sourceFile.exists()) {
                return sourceFile;
            }
        }
        
        // Also try to find the file recursively in subdirectories under the base directory
        File foundFile = findFileRecursivelyFromBase(baseDir, sourceFileName);
        if (foundFile != null) {
            return foundFile;
        }
        
        return null;
    }
    
    /**
     * Recursively finds a file with the given name in the directory tree starting from baseDir
     * This method is kept for backward compatibility and handles case-sensitive file name matching
     */
    private File findFileRecursivelyFromBase(File baseDir, String fileName) {
        return findFileRecursively(baseDir, fileName, false); // Case sensitive by default
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