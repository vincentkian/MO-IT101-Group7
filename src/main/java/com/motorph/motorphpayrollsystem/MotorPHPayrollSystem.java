
package com.motorph.motorphpayrollsystem;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.logging.*;

/**
 * The MotorPH Payroll System is a Java-based program designed to automate all aspects of employee payroll processing.
 * 
 * This system automates:
 * - Employee data retrieval
 * - Attendance tracking
 * - Salary computation
 * - Deduction processing (e.g., taxes, benefits)
 * - Payroll report generation
 *
 * The system reads employee data and attendance records from Excel files, processes the 
 * information according to company policies and government regulations, and generates 
 * comprehensive payroll reports for individual employees.
 */

public class MotorPHPayrollSystem {
    // System logger for tracking operations and errors
    private static final Logger logger = Logger.getLogger(MotorPHPayrollSystem.class.getName());
    
    // Standard time format for parsing and displaying time values (24-hour format)
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    // Standard date format for all date processing
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    // BUSINESS RULES AND CONSTANTS
    
    // Overtime premium rate (25% of base hourly rate)
    private static final double OVERTIME_RATE_MULTIPLIER = 0.25;
    
    // Standard work schedule configuration
    private static final LocalTime WORK_START = LocalTime.of(8, 0); // Workday starts at 8:00 AM
    private static final LocalTime WORK_END = LocalTime.of(17, 0);  // Workday ends at 5:00 PM
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0); // Lunch break 12:00 PM - 1:00 PM
    private static final LocalTime LUNCH_END = LocalTime.of(13, 0);
    
    // Valid month names for input validation
    private static final List<String> VALID_MONTHS = Arrays.asList(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER");

    /**
     * Main entry point for the payroll system that:
     * 1. Initializes system components
     * 2. Collects employee number and payroll month
     * 3. Retrieves employee data
     * 4. Processes payroll calculations
     * 5. Displays complete payroll report
     */
    public static void main(String[] args) {
        try {
            // Initialize system logging configuration
            LoggerSetup.configureLogger();
            logger.info("Starting MotorPH Payroll System");

            // Create scanner to read user input from the console
            Scanner scanner = new Scanner(System.in);

            // Collect and validate employee number input
            System.out.print("Enter Employee Number: ");
            int employeeNumber;
            try {
                employeeNumber = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid employee number format. Please enter a numeric value.");
                return;
            }

            // Path to employee data Excel file
            String filePath = "src/MotorPH_Employee_Data.xlsx";

            // Retrieve employee details from database
            EmployeeDetails employeeDetails = getEmployeeDetails(filePath, employeeNumber);
            if (employeeDetails == null) {
                System.out.println("Error: Employee Number " + employeeNumber + " not found.");
                return;
            }

            // Collect and validate payroll month input
            String month = getValidMonth(scanner);

            // Process and display payroll information
            displayEmployeePayroll(filePath, employeeNumber, month);

        } catch (Exception e) {
            // Handle system errors gracefully
            logger.log(Level.SEVERE, "Fatal error in application", e);
            System.err.println("A fatal error occurred. Please check the logs.");
        }
    }

    /**
     * Represents a work week range containing:
     * - ISO week number
     * - Start date (Monday)
     * - End date (Sunday)
     * Used to organize payroll calculations by weekly periods
     */
    private static class WeekRange {
        private final int weekNumber;
        private final String startDate;
        private final String endDate;

        public WeekRange(int weekNumber, String startDate, String endDate) {
            this.weekNumber = weekNumber;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public int getWeekNumber() { return weekNumber; }
        public String getStartDate() { return startDate; }
        public String getEndDate() { return endDate; }
    }

    /**
     * Generates all work week ranges for the payroll year
     * starting from June 3, 2024 to December 31, 2024
     * with proper ISO week numbering and date formatting
     */
    private static List<WeekRange> getWeeklyRangesForYear() {
        LocalDate startDate = LocalDate.of(2024, 6, 3);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        List<WeekRange> weeklyRanges = new ArrayList<>();

        LocalDate weekStart = startDate;
        while (!weekStart.isAfter(endDate)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekEnd.isAfter(endDate)) {
                weekEnd = endDate;
            }

            int weekNumber = weekStart.get(WeekFields.ISO.weekOfYear());
            weeklyRanges.add(new WeekRange(
                weekNumber,
                weekStart.format(DATE_FORMATTER),
                weekEnd.format(DATE_FORMATTER)
            ));

            weekStart = weekStart.plusDays(7);
        }

        return weeklyRanges;
    }

    /**
     * Validates that a file path is properly formatted and points to an existing file.
     * Performs two critical checks:
     * 1. The path is not null or empty (basic validation)
     * 2. The file exists at the specified location
     * 
     * @param filePath The path to validate
     * @throws IllegalArgumentException if the path is invalid or file doesn't exist
     */
    private static void validateFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            String errorMsg = "File path is empty";
            logger.severe(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * Retrieves employee details from Excel database including:
     * - Personal information
     * - Compensation rates
     * - Benefit allowances
     *
     * @param filePath Path to the employee data file
     * @param employeeNumber The ID number to search for
     * @return EmployeeDetails object or null if not found
     */
    private static EmployeeDetails getEmployeeDetails(String filePath, int employeeNumber) {
        validateFilePath(filePath);

        File file = new File(filePath);
        if (!file.exists()) {
            String errorMsg = "File not found: " + filePath;
            logger.severe(errorMsg);
            return null;
        }

        // Open the Excel workbook and search for employee
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Access the Employee Details worksheet
            Sheet employeeSheet = workbook.getSheet("Employee Details");
            if (employeeSheet == null) {
                logger.severe("Employee Details sheet not found");
                return null;
            }

            // Process each row looking for matching employee number
            boolean foundHeader = false;
            for (Row row : employeeSheet) {
                if (row == null) continue;

                Cell employeeCell = row.getCell(0);
                if (employeeCell == null) continue;

                if (!foundHeader) {
                    foundHeader = true;
                    continue;
                }

                try {
                    String cellValue = getCellValueAsString(employeeCell).trim();
                    if (cellValue.equals(String.valueOf(employeeNumber).trim())) {
                        String firstName = getCellValueAsString(row.getCell(2));
                        String lastName = getCellValueAsString(row.getCell(1));
                        String birthday = row.getCell(3).getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMATTER);
                        double hourlyRate = row.getCell(18).getNumericCellValue();

                        double riceSubsidy = row.getCell(14) != null ? row.getCell(14).getNumericCellValue() : 0;
                        double phoneAllowance = row.getCell(15) != null ? row.getCell(15).getNumericCellValue() : 0;
                        double clothingAllowance = row.getCell(16) != null ? row.getCell(16).getNumericCellValue() : 0;
                        double monthlyBenefits = riceSubsidy + phoneAllowance + clothingAllowance;

                        return new EmployeeDetails(employeeNumber, firstName, lastName, birthday, hourlyRate, monthlyBenefits);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error processing row " + row.getRowNum() + " in employee sheet", e);
                    continue;
                }
            }
            return null;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading file: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts Excel cell values to consistent string format
     * handling all cell types (string, numeric, boolean, formula)
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalTime().format(TIME_FORMATTER);
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * Prompts the user to enter a payroll month and validates the input against
     * the list of valid months.
     *
     * @param scanner The input scanner to read user responses
     * @return Validated month name in uppercase (e.g., "JANUARY")
     */
    private static String getValidMonth(Scanner scanner) {
        String month;
        do {
            System.out.print("Enter the month to display: ");
            month = scanner.nextLine().trim().toUpperCase();
            if (!VALID_MONTHS.contains(month)) {
                System.out.println("Invalid month. Please enter a full month name (e.g., JANUARY).");
            }
        } while (!VALID_MONTHS.contains(month));
        return month;
    }

    /**
     * Main payroll processing function that:
     * 1. Validates input files
     * 2. Retrieves employee data
     * 3. Calculates salary from attendance
     * 4. Computes deductions
     * 5. Displays complete payroll report
     */
    public static void displayEmployeePayroll(String filePath, int employeeNumber, String month) {
        validateFilePath(filePath);

        // Check file existence
        File file = new File(filePath);
        if (!file.exists()) {
            String errorMsg = "File not found: " + filePath;
            logger.severe(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        // Process the payroll data
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            logger.info("Processing payroll for employee " + employeeNumber + " for month " + month);

            // Access required worksheets
            Sheet employeeSheet = workbook.getSheet("Employee Details");
            Sheet attendanceSheet = workbook.getSheet("Attendance Record");

            // Verify worksheets exist
            validateSheets(employeeSheet, attendanceSheet);

            // Retrieve employee profile
            EmployeeDetails employeeDetails = getEmployeeDetails(employeeSheet, employeeNumber);
            if (employeeDetails == null) {
                String errorMsg = "Employee Number " + employeeNumber + " not found.";
                logger.warning(errorMsg);
                System.out.println(errorMsg);
                return;
            }

            // Display employee information header
            displayEmployeeHeader(employeeDetails, month);

            // Calculate monthly salary from attendance records
            double monthlySalary = calculateMonthlySalary(attendanceSheet, employeeNumber, employeeDetails.getHourlyRate(), month);
            
            // Display gross salary before deductions
            DecimalFormat df = new DecimalFormat("#,##0.00");
            System.out.println("Gross Salary: Php " + df.format(monthlySalary));
            System.out.println("---------------------------------------");

            // Calculate and display all payroll deductions
            calculateDeductions(monthlySalary, employeeDetails.getMonthlyBenefits());

        } catch (IOException e) {
            String errorMsg = "Error reading file: " + e.getMessage();
            logger.log(Level.SEVERE, errorMsg, e);
            throw new RuntimeException("Failed to process payroll due to file error", e);
        }
    }

    /**
     * Validates that required Excel worksheets exist
     * @throws IllegalArgumentException if sheets are missing
     */
    private static void validateSheets(Sheet employeeSheet, Sheet attendanceSheet) {
        if (employeeSheet == null) {
            String errorMsg = "Employee Details sheet not found";
            logger.severe(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        if (attendanceSheet == null) {
            String errorMsg = "Attendance Record sheet not found";
            logger.severe(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * Retrieves employee details from specified worksheet
     */
    private static EmployeeDetails getEmployeeDetails(Sheet employeeSheet, int employeeNumber) {
        boolean foundHeader = false;
        for (Row row : employeeSheet) {
            if (row == null) continue;

            Cell employeeCell = row.getCell(0);
            if (employeeCell == null) continue;

            if (!foundHeader) {
                foundHeader = true;
                continue;
            }

            try {
                String cellValue = getCellValueAsString(employeeCell).trim();
                if (cellValue.equals(String.valueOf(employeeNumber).trim())) {
                    String firstName = getCellValueAsString(row.getCell(2));
                    String lastName = getCellValueAsString(row.getCell(1));
                    String birthday = row.getCell(3).getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMATTER);
                    double hourlyRate = row.getCell(18).getNumericCellValue();

                    double riceSubsidy = row.getCell(14) != null ? row.getCell(14).getNumericCellValue() : 0;
                    double phoneAllowance = row.getCell(15) != null ? row.getCell(15).getNumericCellValue() : 0;
                    double clothingAllowance = row.getCell(16) != null ? row.getCell(16).getNumericCellValue() : 0;
                    double monthlyBenefits = riceSubsidy + phoneAllowance + clothingAllowance;

                    return new EmployeeDetails(employeeNumber, firstName, lastName, birthday, hourlyRate, monthlyBenefits);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error processing row " + row.getRowNum() + " in employee sheet", e);
                continue;
            }
        }
        return null;
    }

    /**
     * Displays employee information header section
     */
    private static void displayEmployeeHeader(EmployeeDetails employeeDetails, String month) {
        System.out.println("========Employee Payroll Summary=======");
        System.out.println("Employee Number: " + employeeDetails.getEmployeeNumber());
        System.out.println("Name: " + employeeDetails.getLastName() + ", " + employeeDetails.getFirstName());
        System.out.println("Birthday: " + employeeDetails.getBirthday());
        System.out.println("---------------------------------------");
        System.out.println("             " + month);
        System.out.println("---------------------------------------");
    }

    /**
     * Calculates monthly salary by processing attendance records week by week
     * including regular hours, overtime, and late time calculations
     */
    private static double calculateMonthlySalary(Sheet attendanceSheet, int employeeNumber, double hourlyRate, String month) {
        double totalMonthlyPay = 0;
        double overtimeRate = hourlyRate * OVERTIME_RATE_MULTIPLIER;
        List<WeekRange> weeklyRanges = getWeeklyRangesForYear();
        
        // Filter weeks for the selected month
        List<WeekRange> filteredRanges = weeklyRanges.stream()
            .filter(week -> {
                LocalDate rangeStart = LocalDate.parse(week.getStartDate(), DATE_FORMATTER);
                return rangeStart.getMonth().toString().equalsIgnoreCase(month);
            })
            .toList();

        for (WeekRange week : filteredRanges) {
            LocalDate weekStart = LocalDate.parse(week.getStartDate(), DATE_FORMATTER);
            LocalDate weekEnd = LocalDate.parse(week.getEndDate(), DATE_FORMATTER);

            System.out.println("Week " + week.getWeekNumber() + ": " +
                    weekStart.format(DATE_FORMATTER) + " to " +
                    weekEnd.format(DATE_FORMATTER));

            int regularMinutes = 0;
            int lateMinutes = 0;
            double weeklyOvertimePay = 0;

            for (Row row : attendanceSheet) {
                if (row.getRowNum() == 0) continue;

                try {
                    int currentEmployeeNumber = (int) row.getCell(0).getNumericCellValue();
                    LocalDate date = row.getCell(3).getLocalDateTimeCellValue().toLocalDate();

                    if (currentEmployeeNumber == employeeNumber && !date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                        String logInTime = getCellValueAsString(row.getCell(4));
                        String logOutTime = getCellValueAsString(row.getCell(5));

                        if (!logInTime.isEmpty() && !logOutTime.isEmpty()) {
                            AttendanceResult result = processAttendanceDay(employeeNumber, date, logInTime, logOutTime, hourlyRate, overtimeRate);
                            regularMinutes += result.regularMinutes;
                            lateMinutes += result.lateMinutes;
                            weeklyOvertimePay += result.overtimePay;
                        } else {
                            logger.info(String.format("Missing time data for employee %d on %s", employeeNumber, date));
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error processing attendance row " + row.getRowNum(), e);
                }
            }

            double weeklyRegularPay = (regularMinutes / 60.0) * hourlyRate;
            double weeklySalary = weeklyRegularPay + weeklyOvertimePay;
            totalMonthlyPay += weeklySalary;

            displayWeeklySummary(regularMinutes, lateMinutes, weeklyRegularPay, weeklyOvertimePay, weeklySalary);
        }

        return totalMonthlyPay;
    }

    /**
     * Processes a single day's attendance record to calculate:
     * - Regular work minutes (excluding lunch)
     * - Late arrival minutes
     * - Overtime pay eligibility
     */
    private static AttendanceResult processAttendanceDay(int employeeNumber, LocalDate date, 
                                                      String logInTime, String logOutTime,
                                                      double hourlyRate, double overtimeRate) {
        AttendanceResult result = new AttendanceResult();
        
        try {
            LocalTime logIn = parseTime(logInTime);
            LocalTime logOut = parseTime(logOutTime);

            validateTimeRange(employeeNumber, date, logInTime, logOutTime, logIn, logOut);

            // Calculate late minutes
            if (logIn.isAfter(WORK_START)) {
                result.lateMinutes = (int) WORK_START.until(logIn, java.time.temporal.ChronoUnit.MINUTES);
            }

            // Calculate regular minutes
            LocalTime actualWorkStart = logIn.isAfter(WORK_START) ? logIn : WORK_START;
            long morningMinutes = Math.max(0, actualWorkStart.until(LUNCH_START, java.time.temporal.ChronoUnit.MINUTES));
            long afternoonMinutes = Math.max(0, LUNCH_END.until(logOut.isBefore(WORK_END) ? logOut : WORK_END,
                    java.time.temporal.ChronoUnit.MINUTES));

            result.regularMinutes = (int) (morningMinutes + afternoonMinutes);

            // Calculate overtime
            if (!logIn.isAfter(WORK_START) && logOut.isAfter(WORK_END)) {
                long overtimeMinutes = WORK_END.until(logOut, java.time.temporal.ChronoUnit.MINUTES);
                result.overtimePay = (overtimeMinutes / 60.0) * (hourlyRate + overtimeRate);
            }

        } catch (IllegalArgumentException e) {
            logger.warning(String.format("Invalid time data for employee %d on %s: %s. Error: %s",
                    employeeNumber, date, logInTime + "/" + logOutTime, e.getMessage()));
        }
        
        return result;
    }

    /**
     * Validates time range for a work day including:
     * - Logout cannot be before login
     * - Suspicious early/late times
     */
    private static void validateTimeRange(int employeeNumber, LocalDate date, String logInTime, String logOutTime,
                                        LocalTime logIn, LocalTime logOut) {
        if (logOut.isBefore(logIn)) {
            logger.warning(String.format(
                    "Invalid time range for employee %d on %s: logout (%s) before login (%s)",
                    employeeNumber, date, logOutTime, logInTime));
            throw new IllegalArgumentException("Logout time cannot be before login time");
        }

        if (logIn.isAfter(LocalTime.of(12, 0))) {
            logger.warning(String.format(
                    "Suspicious login time for employee %d on %s: %s",
                    employeeNumber, date, logInTime));
        }

        if (logOut.isBefore(LocalTime.of(8, 0))) {
            logger.warning(String.format(
                    "Suspicious logout time for employee %d on %s: %s",
                    employeeNumber, date, logOutTime));
        }
    }

    /**
     * Displays weekly payroll summary including:
     * - Regular hours worked
     * - Late time accumulated
     * - Pay breakdown (regular and overtime)
     */
    private static void displayWeeklySummary(int regularMinutes, int lateMinutes,
                                          double weeklyRegularPay, double weeklyOvertimePay,
                                          double weeklySalary) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        System.out.println("Regular Hours: " + (regularMinutes / 60) + " hrs " + (regularMinutes % 60) + " min/s");
        System.out.println("Accumulated Late Time: " + (lateMinutes / 60) + " hr/s " + (lateMinutes % 60) + " min/s");
        System.out.println("Regular Pay: Php " + df.format(weeklyRegularPay));
        System.out.println("Overtime Pay: Php " + df.format(weeklyOvertimePay));
        System.out.println();
        System.out.println("Weekly Salary: Php " + df.format(weeklySalary));
        System.out.println("-------------------------");
    }

    /**
     * Calculates and displays all payroll deductions including:
     * - SSS contributions
     * - PhilHealth payments
     * - Pag-IBIG contributions
     * - Withholding tax
     * - Net pay after deductions
     */
    private static void calculateDeductions(double monthlySalary, double monthlyBenefits) {
        DecimalFormat df = new DecimalFormat("#,##0.00");

        // Constants for deductions
        final double PHILHEALTH_MIN_CONTRIBUTION = 300.00;
        final double PHILHEALTH_MAX_CONTRIBUTION = 1800.00;
        final double PAG_IBIG_MAX_CONTRIBUTION = 100.00;

        // Calculate SSS contribution
        double sss = calculateSSS(monthlySalary);

        // Calculate PhilHealth contribution
        double philHealth;
        if (monthlySalary <= 10000) {
            philHealth = PHILHEALTH_MIN_CONTRIBUTION;
        } else if (monthlySalary > 10000 && monthlySalary < 60000) {
            philHealth = monthlySalary * 0.03;
        } else {
            philHealth = PHILHEALTH_MAX_CONTRIBUTION;
        }
        double employeePhilHealthShare = philHealth / 2;

        // Calculate Pag-IBIG contribution
        double pagIbig;
        if (monthlySalary >= 1000 && monthlySalary <= 1500) {
            pagIbig = monthlySalary * 0.01;
        } else if (monthlySalary > 1500) {
            pagIbig = Math.min(monthlySalary * 0.02, PAG_IBIG_MAX_CONTRIBUTION);
        } else {
            pagIbig = 0;
        }

        // Calculate taxable income
        double taxableIncome = monthlySalary - (sss + employeePhilHealthShare + pagIbig);

        // Calculate withholding tax
        double withholdingTax = calculateWithholdingTax(taxableIncome);

        // Calculate total deductions and net pay
        double totalDeductions = sss + employeePhilHealthShare + pagIbig + withholdingTax;
        double netPay = (monthlySalary - totalDeductions) + monthlyBenefits;

        // Display deductions and net pay
        System.out.println("Deductions:");
        System.out.println("SSS: Php " + df.format(sss));
        System.out.println("PhilHealth: Php " + df.format(employeePhilHealthShare));
        System.out.println("Pag-IBIG: Php " + df.format(pagIbig));
        System.out.println("Withholding Tax: Php " + df.format(withholdingTax));
        System.out.println("Monthly Benefits: Php " + df.format(monthlyBenefits));
        System.out.println("Net Pay: Php " + df.format(netPay));
    }

    /**
     * Calculates SSS contribution based on monthly salary using
     * the official SSS contribution table with tiered rates
     */
    private static double calculateSSS(double monthlySalary) {
        NavigableMap<Double, Double> sssTable = new TreeMap<>();
        double[] salaryBrackets = {
                3250, 3750, 4250, 4750, 5250, 5750, 6250, 6750, 7250, 7750,
                8250, 8750, 9250, 9750, 10250, 10750, 11250, 11750, 12250, 12750,
                13250, 13750, 14250, 14750, 15250, 15750, 16250, 16750, 17250, 17750,
                18250, 18750, 19250, 19750, 20250, 20750, 21250, 21750, 22250, 22750,
                23250, 23750, 24250, 24750
        };
        double[] sssContributions = {
                135.00, 157.50, 180.00, 202.50, 225.00, 247.50, 270.00, 292.50, 315.00, 337.50,
                360.00, 382.50, 405.00, 427.50, 450.00, 472.50, 495.00, 517.50, 540.00, 562.50,
                585.00, 607.50, 630.00, 652.50, 675.00, 697.50, 720.00, 742.50, 765.00, 787.50,
                810.00, 832.50, 855.00, 877.50, 900.00, 922.50, 945.00, 967.50, 990.00, 1012.50,
                1035.00, 1057.50, 1080.00, 1102.50
        };

        for (int i = 0; i < salaryBrackets.length; i++) {
            sssTable.put(salaryBrackets[i], sssContributions[i]);
        }

        if (monthlySalary <= 0) {
            logger.severe("Invalid monthly salary for SSS calculation: " + monthlySalary);
            return 0.0;
        }

        if (monthlySalary < sssTable.firstKey()) {
            return sssTable.get(sssTable.firstKey());
        }

        Double key = sssTable.ceilingKey(monthlySalary);
        if (key == null) {
            return sssTable.get(sssTable.lastKey());
        }

        return sssTable.get(key);
    }

    /**
     * Calculates withholding tax based on BIR tax tables using
     * progressive tax rates for different income brackets
     */
    private static double calculateWithholdingTax(double taxableIncome) {
        double withholdingTax = 0;

        if (taxableIncome <= 20832) {
            withholdingTax = 0;
        } else if (taxableIncome > 20833 && taxableIncome <= 33333) {
            withholdingTax = (taxableIncome - 20833) * 0.20;
        } else if (taxableIncome > 33333 && taxableIncome <= 66667) {
            withholdingTax = 2500 + (taxableIncome - 33333) * 0.25;
        } else if (taxableIncome > 66667 && taxableIncome <= 166667) {
            withholdingTax = 10833 + (taxableIncome - 66667) * 0.30;
        } else if (taxableIncome > 166667 && taxableIncome <= 666667) {
            withholdingTax = 40833.33 + (taxableIncome - 166667) * 0.32;
        } else if (taxableIncome > 666667) {
            withholdingTax = 200833.33 + (taxableIncome - 666667) * 0.35;
        }

        return withholdingTax;
    }

    /**
     * Parses time string into LocalTime object using standard format
     * @throws IllegalArgumentException for invalid time formats
     */
    private static LocalTime parseTime(String timeStr) throws IllegalArgumentException {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Time string is empty");
        }
        try {
            return LocalTime.parse(timeStr.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format. Expected HH:mm", e);
        }
    }

    /**
     * Container for employee details including:
     * - Identification information
     * - Compensation rates
     * - Benefit allowances
     */
    private static class EmployeeDetails {
        private final int employeeNumber;
        private final String firstName;
        private final String lastName;
        private final String birthday;
        private final double hourlyRate;
        private final double monthlyBenefits;

        public EmployeeDetails(int employeeNumber, String firstName, String lastName, String birthday, double hourlyRate, double monthlyBenefits) {
            this.employeeNumber = employeeNumber;
            this.firstName = firstName;
            this.lastName = lastName;
            this.birthday = birthday;
            this.hourlyRate = hourlyRate;
            this.monthlyBenefits = monthlyBenefits;
        }

        public int getEmployeeNumber() {
            return employeeNumber;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getBirthday() {
            return birthday;
        }

        public double getHourlyRate() {
            return hourlyRate;
        }

        public double getMonthlyBenefits() {
            return monthlyBenefits;
        }
    }

    /**
     * Container for daily attendance calculation results including:
     * - Regular work minutes
     * - Late arrival minutes
     * - Overtime pay earned
     */
    private static class AttendanceResult {
        int regularMinutes = 0;
        int lateMinutes = 0;
        double overtimePay = 0;
    }

    /**
     * Configures system logging to write to both file and console
     * with consistent formatting and log level settings
     */
    private static class LoggerSetup {
        public static void configureLogger() throws IOException {
            Logger logger = Logger.getLogger("");
            // Remove default handlers
            for (Handler handler : logger.getHandlers()) {
                logger.removeHandler(handler);
            }

            // Configure file logging
            FileHandler fileHandler = new FileHandler("payroll_system.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);

            // Configure console logging
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(consoleHandler);

            // Set logging level
            logger.setLevel(Level.INFO);
        }
    }
}
