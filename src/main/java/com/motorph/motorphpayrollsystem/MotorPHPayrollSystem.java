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

        // Ask the user to enter Employee Number
        System.out.print("Enter Employee Number: ");
        int empNum = scanner.nextInt();
        scanner.nextLine(); //Consume the newline character after integer input

        // Ask the user to enter a valid month for payroll display
        String month;
        do {
            System.out.print("Enter the month to display: ");
            month = scanner.nextLine();
        } while (getDateRangeForMonth(month).isEmpty()); // Ensure valid month input

        // Define file path for employee data Excel file
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File not found: " + filePath);
            return;
        }

        // Display payroll details for the specified employee and month
        displayEmployeePayroll(filePath, empNum, month);
    }

    public static void generatePayrollSummary(String filePath, int empNum, String month) {
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Access the necessary sheets from the Excel file
            Sheet empSheet = workbook.getSheet("Employee Details");
            Sheet attendanceSheet = workbook.getSheet("Attendance Record");

            // Validate if required sheets exist in the Excel file
            if (empSheet == null || attendanceSheet == null) {
                System.out.println(" Error: Required sheets not found in the Excel file.");
                return;
            }

            DecimalFormat df = new DecimalFormat("#,##0.00"); // Format for currency values

             // Variables to store employee details
            double hourlyRate = 0; // Initialize hourly rate
            double monthlyBenefits = 0; // Initialize monthly benefits
            boolean employeeFound = false;


            // Iterate through Employee Details sheet to find the matching employee
            for (Row row : empSheet) {
                Cell employeeCell = row.getCell(0); // Employee number column

                if (employeeCell != null && getCellValueAsString(employeeCell).trim().equals(String.valueOf(employeeNumber).trim())) {
                    employeeFound = true;

                    // Retrieve employee details
                    String firstName = getCellValueAsString(row.getCell(2));
                    String lastName = getCellValueAsString(row.getCell(1));
                    String birthday = row.getCell(3).getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));

                    // Retrieve hourly rate
                    hourlyRate = row.getCell(18).getNumericCellValue(); // Retrieve hourly rate from column 19
                     if (hourlyRate <= 0) {
                        System.out.println("Error: Invalid hourly rate" + empNum);
                        return;
                    }

                    // Retrieve employee benefits from respective columns
                    double riceSubsidy = row.getCell(14) != null ? row.getCell(14).getNumericCellValue() : 0; // Column 15
                    double phoneAllowance = row.getCell(15) != null ? row.getCell(15).getNumericCellValue() : 0; // Column 16
                    double clothingAllowance = row.getCell(16) != null ? row.getCell(16).getNumericCellValue() : 0; // Column 17
                    monthlyBenefits = riceSubsidy + phoneAllowance + clothingAllowance;

                    // Display employee details and payroll header
                    System.out.println("========Employee Payroll Summary=======");
                    System.out.println("Employee Number: " + empNum);
                    System.out.println("Name: " + lastName + ", " + firstName);
                    System.out.println("Birthday: " + birthday);
                    System.out.println("---------------------------------------");
                    System.out.println("             " + month);
                    System.out.println("---------------------------------------");

                    // Calculate and display weekly pay for the employee
                    double monthlySalary = calculateWeeklyPay(attendanceSheet, empNum, hourlyRate, df, month);

                    // Calculate deductions and final net pay
                    calculateDeductions(monthlySalary, df, monthlyBenefits);

                    return; // Exit after processing the employee
                }
            }

            // If employee number is not found, display an error message
            if (!employeeFound) {
                System.out.println("Error: Employee Number " + empNum + " not found. Please try again.");
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }

    public static double calculateWeeklyPay(Sheet attendanceSheet, int empNum, double hourlyRate, DecimalFormat df, String month) {
        double totalMonthlyPay = 0;
        double overtimeRate = hourlyRate * 0.25; // Overtime = 25% of hourly rate

        // Get weekly ranges for the specific month
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

            for (Row row : attendanceSheet) {
                if (row.getRowNum() == 0) {
                continue; // Skip header row
                }
                
                // Retrieve employee number and date
                int empNum = (int) row.getCell(0).getNumericCellValue();
                LocalDate date = row.getCell(3).getLocalDateTimeCellValue().toLocalDate();

                
                // Process attendance only if it matches the employee and falls within the week
               if (empNum == employeeNumber && !date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                    // Retrieve log in and log out times
                    String logInTime = getCellValueAsString(row.getCell(4)); // Log In (HH:mm)
                    String logOutTime  = getCellValueAsString(row.getCell(5)); // Log Out (HH:mm)

                    if (!logInTime.isEmpty() && !logOutTime.isEmpty()) {
                        try {
                            // Parse time strings into LocalTime objects
                            LocalTime logIn = LocalTime.parse(logInTime, DateTimeFormatter.ofPattern("HH:mm"));
                            LocalTime logOut = LocalTime.parse(logOutTime , DateTimeFormatter.ofPattern("HH:mm"));
                          
                            // Compute total minutes worked and late minutes
                            LocalTime workStart = LocalTime.of(8, 0);
                            LocalTime workEnd = LocalTime.of(17, 0);
                            LocalTime lunchStart = LocalTime.of(12, 0);
                            LocalTime lunchEnd = LocalTime.of(13, 0);

                            if (logIn.isAfter(workStart)) {
                                lateMinutes += workStart.until(logIn, java.time.temporal.ChronoUnit.MINUTES);
                            }

                            LocalTime actualWorkStart = logIn.isAfter(workStart) ? logIn : workStart;
                            long morningMinutes = Math.max(0, actualWorkStart.until(lunchStart, java.time.temporal.ChronoUnit.MINUTES));
                            long afternoonMinutes = Math.max(0, lunchEnd.until(logOut.isBefore(workEnd) ? logOut : workEnd, java.time.temporal.ChronoUnit.MINUTES));

                            regularMinutes += (morningMinutes + afternoonMinutes);

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

            weeklyRegularPay = (regularMinutes / 60.0) * hourlyRate;
            double weeklySalary = weeklyRegularPay + weeklyOvertimePay;
            totalMonthlyPay += weeklySalary;

            // Display weekly summary
            System.out.println("Regular Hours: " + (regularMinutes / 60) + " hrs " + (regularMinutes % 60) + " min/s");
            System.out.println("Accumulated Late Time: " + (lateMinutes / 60) + " hr/s " + (lateMinutes % 60) + " min/s");
            System.out.println("Regular Pay: Php " + df.format(weeklyRegularPay));
            System.out.println("Overtime Pay: Php " + df.format(weeklyOvertimePay));
            System.out.println();
            System.out.println("Weekly Salary: Php " + df.format(weeklySalary));
            System.out.println("-------------------------");
        }

        return totalMonthlyPay; // Return the total pay for the month
    }

    private static List<String[]> getDateRangeForMonth(String month) {
        
        // Define the start and end dates for the payroll system's period
        LocalDate startDate = LocalDate.of(2024, 6, 3); // First working day of June
        LocalDate endDate = LocalDate.of(2024, 12, 31); // Last working day of the year
        List<String[]> weeklyRanges = new ArrayList<>();

        // Generate weekly ranges dynamically from June 3 to December 31
        LocalDate weekStart = startDate;
        while (!weekStart.isAfter(endDate)) {
            LocalDate weekEnd = weekStart.plusDays(6); // Define week-end as 6 days after start
            if (weekEnd.isAfter(endDate)) {
                weekEnd = endDate; // Ensure the last week's range does not exceed endDate
            }

            // Store the week range in MM/dd/yyyy format
            weeklyRanges.add(new String[]{
                weekStart.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
                weekEnd.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))
            });

            weekStart = weekStart.plusDays(7); // Move to the next week
        }

        // Filter only the weeks that belong to the specified month
        List<String[]> filteredRanges = new ArrayList<>();
        for (String[] range : weeklyRanges) {
            LocalDate rangeStart = LocalDate.parse(range[0], DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            if (rangeStart.getMonth().toString().equalsIgnoreCase(month)) {
                filteredRanges.add(range);
            }
        }

        return filteredRanges; // Return the filtered list of weekly date ranges
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return ""; // Return an empty string for null cells
        }

        // Determine the cell type and extract its value accordingly
        switch (cell.getCellType()) {
            case STRING -> {
                return cell.getStringCellValue();
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // If the cell contains a date, format it as HH:mm (time)
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                    return cell.getLocalDateTimeCellValue().toLocalTime().format(timeFormatter);
                } else {
                    // Convert numeric values to long (for whole numbers) and return as string
                    return String.valueOf((long) cell.getNumericCellValue()); 
                }
            }
            case BOOLEAN -> {
                return String.valueOf(cell.getBooleanCellValue()); //Convert boolean to String 
            }
            case FORMULA -> {
                return cell.getCellFormula();
            }
            default -> {
                return ""; // Return empty string for unsupported types
            }
        }
    }

        private static void calculateDeductions(double monthlySalary, DecimalFormat df, double monthlyBenefits) {

        // Compute SSS Contribution based on salary brackets
        double sss = calculateSSS(monthlySalary);

        // Compute PhilHealth Contribution with capped values
        double philHealth;
        if (monthlySalary <= 10000) {
            philHealth = 300.00; // Minimum contribution
        } 
        else if (monthlySalary > 10000 && monthlySalary < 60000) {
            philHealth = monthlySalary * 0.03; // 3% of monthly salary
        } 
        else {
            philHealth = 1800.00; // Maximum cap
        }
        double employeePhilHealthShare = philHealth / 2; // Employee share (50%)
        
        // Compute Pag-IBIG Contribution with salary-based rates
        double pagIbig;
        if (monthlySalary >= 1000 && monthlySalary <= 1500) {
            pagIbig = monthlySalary * 0.01; // Employee Share is 1% for salaries between 1,000 and 1,500
        } 
        else if (monthlySalary > 1500) {
            pagIbig = Math.min(monthlySalary * 0.02, 100.00); // Employee Share is 2%, capped at 100
        } 
        else {
            pagIbig = 0; // No contribution for salaries below 1,000
        }


       // Compute taxable income after deducting mandatory contributions
        double taxableIncome = monthlySalary - (sss + employeePhilHealthShare + pagIbig);

        // Compute Withholding Tax based on taxable income
        double withholdingTax = calculateWithholdingTax(taxableIncome);

        // Compute total deductions and final net salar
        double totalDeductions = sss + employeePhilHealthShare + pagIbig + withholdingTax;
        double netPay = (monthlySalary - totalDeductions) + monthlyBenefits; // Add benefits to net pay after deductions

        // Display deduction breakdown and net salary
        System.out.println("Deductions:");
        System.out.println("SSS: Php " + df.format(sss));
        System.out.println("PhilHealth: Php " + df.format(employeePhilHealthShare));
        System.out.println("Pag-IBIG: Php " + df.format(pagIbig));
        System.out.println("Withholding Tax: Php " + df.format(withholdingTax));
        System.out.println("Monthly Benefits: Php " + df.format(monthlyBenefits));
        System.out.println("Net Pay: Php " + df.format(netPay));
    }

   public class ConditionsSSS{
    // TreeMap to store SSS contribution brackets with salary as the key and corresponding SSS contribution as the value
    private static final NavigableMap<Double, Double> sssTable = new TreeMap<>();
    // Static block to initialize the SSS salary brackets and corresponding contribution amounts
    static {
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
        // Populating the TreeMap with salary brackets and their corresponding SSS contributions
        for (int i = 0; i < salaryBrackets.length; i++) {
            sssTable.put(salaryBrackets[i], sssContributions[i]);
        }
    }
    /**
     * Calculates the Social Security System (SSS) contribution based on the given monthly salary.
     *
     * @param monthlySalary The employee's monthly salary.
     * @return The corresponding SSS contribution.
     */
    public static double calculateSSS(double monthlySalary) {
        // If salary is below the minimum bracket, return the lowest contribution
        if (monthlySalary < sssTable.firstKey()) {
            return sssTable.get(sssTable.firstKey());
        }

        // Find the smallest key that is greater than or equal to the salary
        Double key = sssTable.ceilingKey(monthlySalary);
    
        // Return the corresponding contribution
        return sssTable.get(key);
}


       private static double calculateWithholdingTax(double taxableIncome) {
        double withholdingTax = 0; // Initialize withholding tax to 0
    
        // No tax for salaries ≤ 20,832
        if (taxableIncome <= 20832) {
            withholdingTax = 0;  
        } 
        // 20% tax for income exceeding 20,833 up to 33,333
        else if (taxableIncome > 20833 && taxableIncome <= 33333) {
            withholdingTax = (taxableIncome - 20833) * 0.20;  
        } 
        // Php 2,500 base tax + 25% for income exceeding 33,333 up to 66,667
        else if (taxableIncome > 33333 && taxableIncome <= 66667) {
            withholdingTax = 2500 + (taxableIncome - 33333) * 0.25;  
        } 
        // Php 10,833 base tax + 30% for income exceeding 66,667 up to 166,667
        else if (taxableIncome > 66667 && taxableIncome <= 166667) {
            withholdingTax = 10833 + (taxableIncome - 66667) * 0.30;  
        } 
        // Php 40,833.33 base tax + 32% for income exceeding 166,667 up to 666,667
        else if (taxableIncome > 166667 && taxableIncome <= 666667) {
            withholdingTax = 40833.33 + (taxableIncome - 166667) * 0.32;  
        } 
        // Php 200,833.33 base tax + 35% for income exceeding 666,667
        else if (taxableIncome > 666667) {
            withholdingTax = 200833.33 + (taxableIncome - 666667) * 0.35;  
        }
    
        return withholdingTax; // Return the computed withholding tax
    }

}
}
