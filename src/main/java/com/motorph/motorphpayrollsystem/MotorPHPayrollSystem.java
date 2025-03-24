
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

public class MotorPHPayrollSystem {

    private static final Logger logger = Logger.getLogger(MotorPHPayrollSystem.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    // Configuration constants
    private static final double OVERTIME_RATE_MULTIPLIER = 0.25;
    private static final LocalTime WORK_START = LocalTime.of(8, 0);
    private static final LocalTime WORK_END = LocalTime.of(17, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(13, 0);
    private static final List<String> VALID_MONTHS = Arrays.asList(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER");

    public static void main(String[] args) {
        try {
            LoggerSetup.configureLogger();
            logger.info("Starting MotorPH Payroll System");

            Scanner scanner = new Scanner(System.in);

            // Employee number validation
            System.out.print("Enter Employee Number: ");
            int employeeNumber;
            try {
                employeeNumber = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid employee number format. Please enter a numeric value.");
                return;
            }

            // Define file path for employee data
            String filePath = "src/MotorPH_Employee_Data.xlsx";

            // First validate if employee exists
            EmployeeDetails employeeDetails = getEmployeeDetails(filePath, employeeNumber);
            if (employeeDetails == null) {
                System.out.println("Error: Employee Number " + employeeNumber + " not found.");
                return;
            }

            // Only ask for month if employee is valid
            String month = getValidMonth(scanner);

            // Display payroll details
            displayEmployeePayroll(filePath, employeeNumber, month);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fatal error in application", e);
            System.err.println("A fatal error occurred. Please check the logs.");
        }
    }

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

    private static List<WeekRange> getWeeklyRangesForYear() {
        LocalDate startDate = LocalDate.of(2024, 6, 3); // First working day of June
        LocalDate endDate = LocalDate.of(2024, 12, 31); // Last working day of the year
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

    private static void validateFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            String errorMsg = "File path is empty";
            logger.severe(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }

    private static EmployeeDetails getEmployeeDetails(String filePath, int employeeNumber) {
        validateFilePath(filePath);

        File file = new File(filePath);
        if (!file.exists()) {
            String errorMsg = "File not found: " + filePath;
            logger.severe(errorMsg);
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet employeeSheet = workbook.getSheet("Employee Details");
            if (employeeSheet == null) {
                logger.severe("Employee Details sheet not found");
                return null;
            }

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

    public static void displayEmployeePayroll(String filePath, int employeeNumber, String month) {
        validateFilePath(filePath);

        File file = new File(filePath);
        if (!file.exists()) {
            String errorMsg = "File not found: " + filePath;
            logger.severe(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            logger.info("Processing payroll for employee " + employeeNumber + " for month " + month);

            Sheet employeeSheet = workbook.getSheet("Employee Details");
            Sheet attendanceSheet = workbook.getSheet("Attendance Record");

            validateSheets(employeeSheet, attendanceSheet);

            EmployeeDetails employeeDetails = getEmployeeDetails(employeeSheet, employeeNumber);
            if (employeeDetails == null) {
                String errorMsg = "Employee Number " + employeeNumber + " not found.";
                logger.warning(errorMsg);
                System.out.println(errorMsg);
                return;
            }

            displayEmployeeHeader(employeeDetails, month);

            double monthlySalary = calculateMonthlySalary(attendanceSheet, employeeNumber, employeeDetails.getHourlyRate(), month);
            
            // Display Gross Salary before deductions
            DecimalFormat df = new DecimalFormat("#,##0.00");
            System.out.println("Gross Salary: Php " + df.format(monthlySalary));
            System.out.println("---------------------------------------");
            
            calculateDeductions(monthlySalary, employeeDetails.getMonthlyBenefits());

        } catch (IOException e) {
            String errorMsg = "Error reading file: " + e.getMessage();
            logger.log(Level.SEVERE, errorMsg, e);
            throw new RuntimeException("Failed to process payroll due to file error", e);
        }
    }

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

    private static void displayEmployeeHeader(EmployeeDetails employeeDetails, String month) {
        System.out.println("========Employee Payroll Summary=======");
        System.out.println("Employee Number: " + employeeDetails.getEmployeeNumber());
        System.out.println("Name: " + employeeDetails.getLastName() + ", " + employeeDetails.getFirstName());
        System.out.println("Birthday: " + employeeDetails.getBirthday());
        System.out.println("---------------------------------------");
        System.out.println("             " + month);
        System.out.println("---------------------------------------");
    }

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

    private static class AttendanceResult {
        int regularMinutes = 0;
        int lateMinutes = 0;
        double overtimePay = 0;
    }

    private static class LoggerSetup {
        public static void configureLogger() throws IOException {
            Logger logger = Logger.getLogger("");
            for (Handler handler : logger.getHandlers()) {
                logger.removeHandler(handler);
            }

            FileHandler fileHandler = new FileHandler("payroll_system.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(consoleHandler);

            logger.setLevel(Level.INFO);
        }
    }
}
