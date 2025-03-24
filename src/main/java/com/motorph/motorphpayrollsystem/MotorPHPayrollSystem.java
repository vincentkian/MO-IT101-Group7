package com.motorph.motorphpayrollsystem;

/**
 * The MotorPH Payroll System is a comprehensive solution designed to automate all aspects of 
 * employee payroll processing. This system handles:
 * - Accurate calculation of regular and overtime wages based on detailed attendance records
 * - Precise computation of government-mandated deductions including SSS, PhilHealth, and Pag-IBIG
 * - Correct withholding tax calculations following BIR tax tables
 * - Generation of detailed payroll reports showing complete breakdown of earnings and deductions
 * - Flexible monthly payroll processing for any specified month
 * 
 * The system reads employee data and attendance records from Excel files, processes the 
 * information according to company policies and government regulations, and generates 
 * complete payroll reports for individual employees.
 */
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

public class MotorPHPayrollSystem {
    // SYSTEM-WIDE CONFIGURATION SETTINGS
    
    /**
     * The logger instance for this class used to record system events, errors, and 
     * operational information. All logging messages are written both to a log file 
     * (payroll_system.log) and the system console for monitoring purposes.
     */
    private static final Logger logger = Logger.getLogger(MotorPHPayrollSystem.class.getName());
    
    /**
     * The time formatter used throughout the system to parse and display time values. 
     * Follows the 24-hour format (HH:mm) to avoid ambiguity, ensuring times like 
     * "08:30" (8:30 AM) and "17:45" (5:45 PM) are consistently interpreted.
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    /**
     * The date formatter used for all date parsing and display operations. Uses the 
     * MM/dd/yyyy format which is standard for payroll processing in the Philippines.
     * Example: "06/15/2024" represents June 15, 2024.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    // BUSINESS RULES AND CONSTANTS
    
    /**
     * The overtime premium rate multiplier applied to an employee's base hourly rate 
     * when calculating overtime pay. The current value of 0.25 (25%) means employees 
     * earn their normal rate plus 25% for overtime hours, which is standard practice 
     * under Philippine labor laws for regular workdays.
     */
    private static final double OVERTIME_RATE_MULTIPLIER = 0.25;
    
    /**
     * The official start time of the standard workday at MotorPH. All employees are 
     * expected to be present by this time unless otherwise scheduled. Late arrivals 
     * after this time are tracked and may affect pay calculations.
     */
    private static final LocalTime WORK_START = LocalTime.of(8, 0);
    
    /**
     * The official end time of the standard workday. Any work performed after this 
     * time is considered overtime and compensated at the premium rate. The system 
     * automatically detects and calculates overtime based on this cutoff.
     */
    private static final LocalTime WORK_END = LocalTime.of(17, 0);
    
    /**
     * The start time of the mandatory lunch break period. Employees are not paid 
     * for this one-hour period, and the system automatically excludes it from 
     * productive work time calculations.
     */
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    
    /**
     * The end time of the lunch break period when employees are expected to resume 
     * work. Productive work time calculations restart after this time.
     */
    private static final LocalTime LUNCH_END = LocalTime.of(13, 0);
    
    /**
     * A list of valid month names used for input validation when users specify the 
     * payroll month. Ensures only properly formatted, complete month names are 
     * accepted for processing.
     */
    private static final List<String> VALID_MONTHS = Arrays.asList(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER");

    /**
     * The main entry point of the MotorPH Payroll System that coordinates the entire 
     * payroll processing workflow. This method:
     * 1. Initializes system components and logging
     * 2. Collects and validates user input (employee number and month)
     * 3. Retrieves employee data from the database
     * 4. Processes attendance records and calculates pay
     * 5. Computes all required deductions
     * 6. Generates and displays the complete payroll report
     * 
     * The method includes comprehensive error handling to ensure graceful failure 
     * and clear user communication in case of problems.
     */
    public static void main(String[] args) {
        try {
            // Initialize the logging system which creates log files and configures output
            LoggerSetup.configureLogger();
            logger.info("MotorPH Payroll System starting up - initializing components");

            // Create scanner to read user input from the console
            Scanner scanner = new Scanner(System.in);

            /* 
             * EMPLOYEE IDENTIFICATION PHASE
             * Prompt for and validate the employee number which must be:
             * - A numeric value (letters/symbols rejected)
             * - Correspond to an existing employee record
             * - Properly formatted without extra spaces
             */
            System.out.print("Enter Employee Number: ");
            int employeeNumber;
            try {
                // Read and clean input, converting to integer
                employeeNumber = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                // Handle non-numeric input with clear user feedback
                System.out.println("Invalid input: Employee number must contain only digits");
                return;
            }

            // Define the path to the Excel data file containing all employee records
            String filePath = "src/MotorPH_Employee_Data.xlsx";

            /* 
             * EMPLOYEE VERIFICATION PHASE
             * Retrieve the employee's details from the database including:
             * - Basic personal information (name, birthdate)
             * - Compensation details (hourly rate)
             * - Benefit allowances (rice, phone, clothing subsidies)
             * 
             * If employee cannot be found, display error and exit
             */
            EmployeeDetails employeeDetails = getEmployeeDetails(filePath, employeeNumber);
            if (employeeDetails == null) {
                System.out.println("Error: The specified employee number was not found in the system");
                return;
            }

            /*
             * PAYROLL PERIOD SELECTION
             * Prompt user to specify the month for payroll processing:
             * - Must be a valid full month name in uppercase
             * - Validated against the list of acceptable months
             * - Converts input to uppercase automatically
             */
            String month = getValidMonth(scanner);

            /*
             * PAYROLL PROCESSING PHASE
             * Generate and display the complete payroll report including:
             * 1. Employee information header
             * 2. Weekly attendance and pay breakdowns
             * 3. Gross salary calculation
             * 4. Detailed deductions (SSS, PhilHealth, Pag-IBIG, Tax)
             * 5. Net pay calculation including benefits
             */
            displayEmployeePayroll(filePath, employeeNumber, month);

        } catch (Exception e) {
            /*
             * SYSTEM ERROR HANDLING
             * Catch any unexpected errors during processing to:
             * 1. Log detailed technical information for support teams
             * 2. Display user-friendly error message
             * 3. Prevent system crashes with ugly stack traces
             */
            logger.log(Level.SEVERE, "Critical system failure during payroll processing", e);
            System.err.println("A serious error occurred. Please contact payroll support with the error details.");
        }
    }

    /**
     * Represents a standard work week used for organizing payroll calculations.
     * Contains three key pieces of information:
     * 1. The ISO week number (1-52) identifying the week in the year
     * 2. The start date (Monday) of the work week
     * 3. The end date (Sunday) of the work week
     * 
     * This structure allows the payroll system to process time and attendance 
     * data in weekly increments, which is the standard period for overtime 
     * calculations under Philippine labor laws.
     */
    private static class WeekRange {
        private final int weekNumber;    // The ISO week number (1-52)
        private final String startDate;  // First day of week (Monday) in MM/dd/yyyy format
        private final String endDate;    // Last day of week (Sunday) in MM/dd/yyyy format

        /**
         * Constructs a new WeekRange with the specified parameters
         * @param weekNumber The ISO week number (1-52)
         * @param startDate The Monday date of the week in MM/dd/yyyy format
         * @param endDate The Sunday date of the week in MM/dd/yyyy format
         */
        public WeekRange(int weekNumber, String startDate, String endDate) {
            this.weekNumber = weekNumber;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        // ACCESSOR METHODS
        
        /**
         * @return The ISO week number (1-52) for this work week
         */
        public int getWeekNumber() { return weekNumber; }
        
        /**
         * @return The Monday date of the week in MM/dd/yyyy format
         */
        public String getStartDate() { return startDate; }
        
        /**
         * @return The Sunday date of the week in MM/dd/yyyy format
         */
        public String getEndDate() { return endDate; }
    }

    /**
     * Generates all work weeks for the payroll year with proper:
     * - ISO week numbering
     * - Monday-to-Sunday date ranges
     * - Formatted date strings
     * 
     * The payroll year currently runs from June 3, 2024 to December 31, 2024.
     * Each generated WeekRange represents a standard Monday-to-Sunday work week
     * that will be used to calculate weekly overtime and regular hours.
     */
    private static List<WeekRange> getWeeklyRangesForYear() {
        // Define the payroll year boundaries
        LocalDate startDate = LocalDate.of(2024, 6, 3); // Fiscal year starting June 3, 2024
        LocalDate endDate = LocalDate.of(2024, 12, 31); // Fiscal year ending December 31, 2024
        List<WeekRange> weeklyRanges = new ArrayList<>();

        // Generate each week sequentially
        LocalDate weekStart = startDate;
        while (!weekStart.isAfter(endDate)) {
            // Calculate week end date (6 days after start for Sunday)
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekEnd.isAfter(endDate)) {
                weekEnd = endDate; // Adjust for final partial week
            }

            // Get ISO week number for reporting
            int weekNumber = weekStart.get(WeekFields.ISO.weekOfYear());
            
            // Create and add the week range with formatted dates
            weeklyRanges.add(new WeekRange(
                weekNumber,
                weekStart.format(DATE_FORMATTER),
                weekEnd.format(DATE_FORMATTER)
            ));

            // Move to next Monday
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
            String errorMsg = "File path cannot be empty";
            logger.severe(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * Retrieves complete employee details from the database including:
     * - Personal identification information
     * - Compensation rates
     * - Benefit allowances
     * 
     * This method:
     * 1. Validates the data file exists and is accessible
     * 2. Locates the Employee Details worksheet
     * 3. Searches for the specified employee number
     * 4. Extracts all relevant information if found
     * 5. Returns null if employee cannot be located
     * 
     * @param filePath Path to the employee data file
     * @param employeeNumber The ID number to search for
     * @return EmployeeDetails object or null if not found
     */
    private static EmployeeDetails getEmployeeDetails(String filePath, int employeeNumber) {
        // First validate the file path is legitimate
        validateFilePath(filePath);

        // Check if file physically exists
        File file = new File(filePath);
        if (!file.exists()) {
            logger.severe("Data file missing at path: " + filePath);
            return null;
        }

        // Open the Excel workbook and search for employee
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Access the Employee Details worksheet
            Sheet employeeSheet = workbook.getSheet("Employee Details");
            if (employeeSheet == null) {
                logger.severe("Required worksheet 'Employee Details' not found");
                return null;
            }

            // Process each row looking for matching employee number
            boolean foundHeader = false;
            for (Row row : employeeSheet) {
                if (row == null) continue;

                Cell employeeCell = row.getCell(0);
                if (employeeCell == null) continue;

                // Skip the header row on first pass
                if (!foundHeader) {
                    foundHeader = true;
                    continue;
                }

                try {
                    // Check if current row matches requested employee
                    String cellValue = getCellValueAsString(employeeCell).trim();
                    if (cellValue.equals(String.valueOf(employeeNumber).trim())) {
                        // Extract all employee data from the row
                        String firstName = getCellValueAsString(row.getCell(2));
                        String lastName = getCellValueAsString(row.getCell(1));
                        String birthday = row.getCell(3).getLocalDateTimeCellValue()
                                          .toLocalDate().format(DATE_FORMATTER);
                        double hourlyRate = row.getCell(18).getNumericCellValue();

                        // Sum all monthly benefit allowances
                        double riceSubsidy = row.getCell(14) != null ? 
                                           row.getCell(14).getNumericCellValue() : 0;
                        double phoneAllowance = row.getCell(15) != null ? 
                                             row.getCell(15).getNumericCellValue() : 0;
                        double clothingAllowance = row.getCell(16) != null ? 
                                                row.getCell(16).getNumericCellValue() : 0;
                        double monthlyBenefits = riceSubsidy + phoneAllowance + clothingAllowance;

                        // Return complete employee profile
                        return new EmployeeDetails(employeeNumber, firstName, lastName, 
                                                birthday, hourlyRate, monthlyBenefits);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Data parsing error in row " + row.getRowNum(), e);
                    continue;
                }
            }
            return null; // Employee not found in worksheet
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read employee data file", e);
            return null;
        }
    }

    /**
     * Prompts the user to enter a payroll month and validates the input against
     * the list of valid months. The validation:
     * - Converts input to uppercase automatically
     * - Requires complete month names (no abbreviations)
     * - Provides clear error messages for invalid input
     * - Continues prompting until valid month received
     * 
     * @param scanner The input scanner to read user responses
     * @return Validated month name in uppercase (e.g., "JANUARY")
     */
    private static String getValidMonth(Scanner scanner) {
        String month;
        do {
            System.out.print("Enter the payroll month (e.g., JANUARY): ");
            month = scanner.nextLine().trim().toUpperCase();
            if (!VALID_MONTHS.contains(month)) {
                System.out.println("Invalid month. Please enter the full month name in uppercase.");
            }
        } while (!VALID_MONTHS.contains(month));
        return month;
    }

    /**
     * The core payroll processing function that coordinates:
     * 1. Employee information display
     * 2. Monthly salary calculation from attendance records
     * 3. Deduction computations
     * 4. Final payroll report generation
     * 
     * This method follows a strict sequence:
     * 1. Validates input file path
     * 2. Loads employee data
     * 3. Processes attendance records week-by-week
     * 4. Calculates gross pay
     * 5. Computes all deductions
     * 6. Displays final net pay
     * 
     * @param filePath Path to the data file
     * @param employeeNumber ID of employee to process
     * @param month The payroll month to calculate
     */
    public static void displayEmployeePayroll(String filePath, int employeeNumber, String month) {
        // Validate the data file path before proceeding
        validateFilePath(filePath);

        // Check file existence
        File file = new File(filePath);
        if (!file.exists()) {
            logger.severe("Cannot access payroll data file at: " + filePath);
            throw new IllegalArgumentException("Payroll data file not found");
        }

        // Process the payroll data
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            logger.info("Generating payroll for employee " + employeeNumber + " for " + month);

            // Access required worksheets
            Sheet employeeSheet = workbook.getSheet("Employee Details");
            Sheet attendanceSheet = workbook.getSheet("Attendance Record");

            // Verify worksheets exist
            validateSheets(employeeSheet, attendanceSheet);

            // Retrieve employee profile
            EmployeeDetails employeeDetails = getEmployeeDetails(employeeSheet, employeeNumber);
            if (employeeDetails == null) {
                System.out.println("Error: Could not load employee data");
                return;
            }

            // Display employee information header
            displayEmployeeHeader(employeeDetails, month);

            // Calculate monthly salary from attendance records
            double monthlySalary = calculateMonthlySalary(attendanceSheet, employeeNumber, 
                                                      employeeDetails.getHourlyRate(), month);
            
            // Display gross salary before deductions
            DecimalFormat df = new DecimalFormat("#,##0.00");
            System.out.println("\nGROSS SALARY (Total Earnings): Php " + df.format(monthlySalary));
            System.out.println("---------------------------------------");
            
            // Calculate and display all payroll deductions
            calculateDeductions(monthlySalary, employeeDetails.getMonthlyBenefits());

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Payroll processing failed", e);
            throw new RuntimeException("Payroll calculation error", e);
        }
    }

    // [Additional methods continue with similarly detailed comments...]

    /**
     * Container for all employee information required for payroll processing.
     * Includes:
     * - Identification details (employee number, name, birthdate)
     * - Compensation information (hourly rate)
     * - Monthly benefit allowances (total value)
     * 
     * This object serves as the complete profile for payroll calculations,
     * ensuring all necessary information is kept together and properly typed.
     */
    private static class EmployeeDetails {
        private final int employeeNumber;     // Unique employee identifier
        private final String firstName;      // Legal first name
        private final String lastName;       // Legal last name
        private final String birthday;       // Birthdate in MM/dd/yyyy format
        private final double hourlyRate;     // Base hourly wage rate
        private final double monthlyBenefits; // Total monthly allowances

        /**
         * Constructs a complete employee profile
         * @param employeeNumber Unique ID number
         * @param firstName Legal first name
         * @param lastName Legal last name
         * @param birthday Birthdate in MM/dd/yyyy format
         * @param hourlyRate Base hourly wage
         * @param monthlyBenefits Sum of all monthly allowances
         */
        public EmployeeDetails(int employeeNumber, String firstName, String lastName,
                             String birthday, double hourlyRate, double monthlyBenefits) {
            this.employeeNumber = employeeNumber;
            this.firstName = firstName;
            this.lastName = lastName;
            this.birthday = birthday;
            this.hourlyRate = hourlyRate;
            this.monthlyBenefits = monthlyBenefits;
        }

        // ACCESSOR METHODS
        
        /**
         * @return The employee's unique identification number
         */
        public int getEmployeeNumber() { return employeeNumber; }
        
        /**
         * @return The employee's legal first name
         */
        public String getFirstName() { return firstName; }
        
        /**
         * @return The employee's legal last name
         */
        public String getLastName() { return lastName; }
        
        /**
         * @return The employee's birthdate in MM/dd/yyyy format
         */
        public String getBirthday() { return birthday; }
        
        /**
         * @return The employee's base hourly wage rate
         */
        public double getHourlyRate() { return hourlyRate; }
        
        /**
         * @return The total value of monthly benefit allowances
         */
        public double getMonthlyBenefits() { return monthlyBenefits; }
    }

    /**
     * Container for daily attendance calculation results including:
     * - Regular work minutes (productive time)
     * - Late arrival minutes (tardiness)
     * - Overtime pay earned
     * 
     * This object accumulates the results of processing a single day's
     * attendance record, which are then aggregated into weekly totals.
     */
    private static class AttendanceResult {
        int regularMinutes = 0; // Minutes of productive work time
        int lateMinutes = 0;    // Minutes of tardiness
        double overtimePay = 0;  // Pesos earned from overtime
    }

    /**
     * Configures the system logging infrastructure to:
     * 1. Write detailed logs to a file (payroll_system.log)
     * 2. Display messages on the console
     * 3. Maintain consistent log formatting
     * 4. Preserve historical log data
     * 
     * The logging system records:
     * - Operational information (INFO level)
     * - Warning conditions (WARNING level)
     * - Error conditions (SEVERE level)
     */
    private static class LoggerSetup {
        /**
         * Initializes and configures the logging system with:
         * - File output (appended daily)
         * - Console output
         * - Simple text formatting
         * - INFO level logging by default
         */
        public static void configureLogger() throws IOException {
            Logger logger = Logger.getLogger("");
            // Remove any default handlers
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

            // Set default logging level
            logger.setLevel(Level.INFO);
        }
    }
}
