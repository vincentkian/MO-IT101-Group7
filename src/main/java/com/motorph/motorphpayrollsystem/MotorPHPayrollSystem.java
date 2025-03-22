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
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Scanner;

public class MotorPHPayrollSystem {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Ask for employee number
        System.out.print("Enter Employee Number: ");
        int employeeNumber = scanner.nextInt();
        scanner.nextLine(); // Consume the newline character

        // Ask for the month to display
        String month;
        do {
            System.out.print("Enter the month to display: ");
            month = scanner.nextLine();
        } while (getDateRangeForMonth(month).isEmpty());

        // Define file path for employee data
        String filePath = "src/MotorPH_Employee_Data.xlsx";

        // Display payroll details
        displayEmployeePayroll(filePath, employeeNumber, month);
    }

    /**
     * Displays the payroll summary for a specific employee and month.
     *
     * @param filePath       Path to the Excel file containing employee data.
     * @param employeeNumber The employee number to process.
     * @param month          The month for which payroll is calculated.
     */
    public static void displayEmployeePayroll(String filePath, int employeeNumber, String month) {
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Access the required sheets
            Sheet employeeSheet = workbook.getSheet("Employee Details");
            Sheet attendanceSheet = workbook.getSheet("Attendance Record");

            // Validate if sheets exist
            if (employeeSheet == null || attendanceSheet == null) {
                System.out.println("Error: Required sheets not found in the Excel file.");
                return;
            }

            // Find employee details
            EmployeeDetails employeeDetails = getEmployeeDetails(employeeSheet, employeeNumber);
            if (employeeDetails == null) {
                System.out.println("Error: Employee Number " + employeeNumber + " not found.");
                return;
            }

            // Display employee details
            displayEmployeeHeader(employeeDetails, month);

            // Calculate monthly salary
            double monthlySalary = calculateMonthlySalary(attendanceSheet, employeeNumber, employeeDetails.getHourlyRate(), month);

            // Calculate deductions and net pay
            calculateDeductions(monthlySalary, employeeDetails.getMonthlyBenefits());
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }

    /**
     * Retrieves employee details from the Excel sheet.
     *
     * @param employeeSheet  The sheet containing employee details.
     * @param employeeNumber The employee number to search for.
     * @return EmployeeDetails object containing employee information, or null if not found.
     */
    private static EmployeeDetails getEmployeeDetails(Sheet employeeSheet, int employeeNumber) {
        for (Row row : employeeSheet) {
            Cell employeeCell = row.getCell(0);

            if (employeeCell != null && getCellValueAsString(employeeCell).trim().equals(String.valueOf(employeeNumber).trim())) {
                String firstName = getCellValueAsString(row.getCell(2));
                String lastName = getCellValueAsString(row.getCell(1));
                String birthday = row.getCell(3).getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                double hourlyRate = row.getCell(18).getNumericCellValue();

                // Retrieve employee benefits
                double riceSubsidy = row.getCell(14) != null ? row.getCell(14).getNumericCellValue() : 0;
                double phoneAllowance = row.getCell(15) != null ? row.getCell(15).getNumericCellValue() : 0;
                double clothingAllowance = row.getCell(16) != null ? row.getCell(16).getNumericCellValue() : 0;
                double monthlyBenefits = riceSubsidy + phoneAllowance + clothingAllowance;

                return new EmployeeDetails(employeeNumber, firstName, lastName, birthday, hourlyRate, monthlyBenefits);
            }
        }
        return null;
    }

    /**
     * Displays the employee payroll header.
     *
     * @param employeeDetails The employee details to display.
     * @param month           The month for which payroll is calculated.
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
     * Calculates the monthly salary for an employee.
     *
     * @param attendanceSheet The sheet containing attendance records.
     * @param employeeNumber  The employee number to process.
     * @param hourlyRate      The employee's hourly rate.
     * @param month           The month for which payroll is calculated.
     * @return The total monthly salary.
     */
    private static double calculateMonthlySalary(Sheet attendanceSheet, int employeeNumber, double hourlyRate, String month) {
        double totalMonthlyPay = 0;
        double overtimeRate = hourlyRate * 0.25; // Overtime rate multiplier

        // Get weekly date ranges for the specified month
        List<String[]> weeklyRanges = getDateRangeForMonth(month);

        for (int week = 0; week < weeklyRanges.size(); week++) {
            String[] range = weeklyRanges.get(week);
            LocalDate weekStart = LocalDate.parse(range[0], DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            LocalDate weekEnd = LocalDate.parse(range[1], DateTimeFormatter.ofPattern("MM/dd/yyyy"));

            System.out.println("Week " + (week + 1) + ": " +
                               weekStart.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) + " to " +
                               weekEnd.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));

            int regularMinutes = 0;
            int lateMinutes = 0;
            double weeklyRegularPay = 0;
            double weeklyOvertimePay = 0;

            // Iterate through attendance records
            for (Row row : attendanceSheet) {
                if (row.getRowNum() == 0) {
                    continue; // Skip header row
                }

                int currentEmployeeNumber = (int) row.getCell(0).getNumericCellValue();
                LocalDate date = row.getCell(3).getLocalDateTimeCellValue().toLocalDate();

                // Process attendance for the current employee and week
                if (currentEmployeeNumber == employeeNumber && !date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                    String logInTime = getCellValueAsString(row.getCell(4));
                    String logOutTime = getCellValueAsString(row.getCell(5));

                    if (!logInTime.isEmpty() && !logOutTime.isEmpty()) {
                        try {
                            LocalTime logIn = LocalTime.parse(logInTime, DateTimeFormatter.ofPattern("HH:mm"));
                            LocalTime logOut = LocalTime.parse(logOutTime, DateTimeFormatter.ofPattern("HH:mm"));

                            LocalTime workStart = LocalTime.of(8, 0);
                            LocalTime workEnd = LocalTime.of(17, 0);
                            LocalTime lunchStart = LocalTime.of(12, 0);
                            LocalTime lunchEnd = LocalTime.of(13, 0);

                            // Calculate late minutes
                            if (logIn.isAfter(workStart)) {
                                lateMinutes += workStart.until(logIn, java.time.temporal.ChronoUnit.MINUTES);
                            }

                            // Calculate regular minutes
                            LocalTime actualWorkStart = logIn.isAfter(workStart) ? logIn : workStart;
                            long morningMinutes = Math.max(0, actualWorkStart.until(lunchStart, java.time.temporal.ChronoUnit.MINUTES));
                            long afternoonMinutes = Math.max(0, lunchEnd.until(logOut.isBefore(workEnd) ? logOut : workEnd, java.time.temporal.ChronoUnit.MINUTES));

                            regularMinutes += (morningMinutes + afternoonMinutes);

                            // Calculate overtime
                            if (!logIn.isAfter(workStart) && logOut.isAfter(workEnd)) {
                                long overtimeMinutes = workEnd.until(logOut, java.time.temporal.ChronoUnit.MINUTES);
                                weeklyOvertimePay += (overtimeMinutes / 60.0) * overtimeRate;
                            }

                        } catch (Exception e) {
                            System.out.println("Error parsing times for date " + date + ": " + e.getMessage());
                        }
                    }
                }
            }

            // Calculate weekly pay
            weeklyRegularPay = (regularMinutes / 60.0) * hourlyRate;
            double weeklySalary = weeklyRegularPay + weeklyOvertimePay;
            totalMonthlyPay += weeklySalary;

            // Display weekly summary
            System.out.println("Regular Hours: " + (regularMinutes / 60) + " hrs " + (regularMinutes % 60) + " min/s");
            System.out.println("Accumulated Late Time: " + (lateMinutes / 60) + " hr/s " + (lateMinutes % 60) + " min/s");
            System.out.println("Regular Pay: Php " + new DecimalFormat("#,##0.00").format(weeklyRegularPay));
            System.out.println("Overtime Pay: Php " + new DecimalFormat("#,##0.00").format(weeklyOvertimePay));
            System.out.println();
            System.out.println("Weekly Salary: Php " + new DecimalFormat("#,##0.00").format(weeklySalary));
            System.out.println("-------------------------");
        }

        return totalMonthlyPay;
    }

    /**
     * Calculates deductions and net pay for an employee.
     *
     * @param monthlySalary    The employee's monthly salary.
     * @param monthlyBenefits  The employee's monthly benefits.
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
     * Calculates the SSS contribution based on the monthly salary.
     *
     * @param monthlySalary The employee's monthly salary.
     * @return The SSS contribution.
     */
    private static double calculateSSS(double monthlySalary) {
        // Initialize SSS contribution table
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

        // Populate the sssTable
        for (int i = 0; i < salaryBrackets.length; i++) {
            sssTable.put(salaryBrackets[i], sssContributions[i]);
        }

        // Validate monthlySalary
        if (monthlySalary <= 0) {
            System.out.println("Error: Invalid monthly salary for SSS calculation: " + monthlySalary);
            return 0.0;
        }

        // If salary is below the minimum bracket, return the lowest contribution
        if (monthlySalary < sssTable.firstKey()) {
            return sssTable.get(sssTable.firstKey());
        }

        // Find the smallest key that is greater than or equal to the salary
        Double key = sssTable.ceilingKey(monthlySalary);

        // If no key is found (e.g., salary is higher than the highest bracket), return the highest contribution
        if (key == null) {
            return sssTable.get(sssTable.lastKey());
        }

        // Return the corresponding contribution
        return sssTable.get(key);
    }

    /**
     * Calculates the withholding tax based on taxable income.
     *
     * @param taxableIncome The employee's taxable income.
     * @return The withholding tax.
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
     * Retrieves the value of a cell as a string.
     *
     * @param cell The cell to retrieve the value from.
     * @return The cell value as a string.
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
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                    return cell.getLocalDateTimeCellValue().toLocalTime().format(timeFormatter);
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
     * Generates weekly date ranges for the specified month.
     *
     * @param month The month for which to generate date ranges.
     * @return A list of weekly date ranges.
     */
    private static List<String[]> getDateRangeForMonth(String month) {
        LocalDate startDate = LocalDate.of(2024, 6, 3); // First working day of June
        LocalDate endDate = LocalDate.of(2024, 12, 31); // Last working day of the year
        List<String[]> weeklyRanges = new ArrayList<>();

        // Generate weekly date ranges
        LocalDate weekStart = startDate;
        while (!weekStart.isAfter(endDate)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekEnd.isAfter(endDate)) {
                weekEnd = endDate;
            }

            weeklyRanges.add(new String[]{
                weekStart.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
                weekEnd.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))
            });

            weekStart = weekStart.plusDays(7);
        }

        // Filter weekly ranges for the specified month
        List<String[]> filteredRanges = new ArrayList<>();
        for (String[] range : weeklyRanges) {
            LocalDate rangeStart = LocalDate.parse(range[0], DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            if (rangeStart.getMonth().toString().equalsIgnoreCase(month)) {
                filteredRanges.add(range);
            }
        }

        return filteredRanges;
    }

    /**
     * Represents employee details.
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
}
