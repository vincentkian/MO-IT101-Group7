
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
import java.util.Scanner;

public class MotorPHPayrollSystem {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Prompt for Employee Number
        System.out.print("Enter Employee Number: ");
        int employeeNumber = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        // Prompt for Month
        String month;
        do {
            System.out.print("Enter the month to display: ");
            month = scanner.nextLine();
        } while (getDateRangeForMonth(month).isEmpty());

        // File Path
        String filePath = "src/MotorPH_Employee_Data.xlsx";

        // Display Employee Payroll
        displayEmployeePayroll(filePath, employeeNumber, month);
    }

    public static void displayEmployeePayroll(String filePath, int employeeNumber, String month) {
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet employeeSheet = workbook.getSheet("Employee Details");
            Sheet attendanceSheet = workbook.getSheet("Attendance Record");

            if (employeeSheet == null || attendanceSheet == null) {
                System.out.println("Required sheets not found in the Excel file.");
                return;
            }

            DecimalFormat df = new DecimalFormat("#,##0.00");

            // Retrieve Employee Details
            double hourlyRate = 0; // Initialize hourly rate
            double monthlyBenefits = 0; // Initialize monthly benefits
            boolean employeeFound = false;

            for (Row row : employeeSheet) {
                Cell employeeCell = row.getCell(0);

                if (employeeCell != null && getCellValueAsString(employeeCell).trim().equals(String.valueOf(employeeNumber).trim())) {
                    employeeFound = true;
                    String firstName = getCellValueAsString(row.getCell(2));
                    String lastName = getCellValueAsString(row.getCell(1));
                    String birthday = row.getCell(3).getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                    hourlyRate = row.getCell(18).getNumericCellValue(); // Retrieve hourly rate from column 19

                    // Retrieve benefits
                    double riceSubsidy = row.getCell(14) != null ? row.getCell(14).getNumericCellValue() : 0; // Column 15
                    double phoneAllowance = row.getCell(15) != null ? row.getCell(15).getNumericCellValue() : 0; // Column 16
                    double clothingAllowance = row.getCell(16) != null ? row.getCell(16).getNumericCellValue() : 0; // Column 17
                    monthlyBenefits = riceSubsidy + phoneAllowance + clothingAllowance;

                    // Print Basic Details
                    System.out.println("========Employee Payroll Summary=======");
                    System.out.println("Employee Number: " + employeeNumber);
                    System.out.println("Name: " + lastName + ", " + firstName);
                    System.out.println("Birthday: " + birthday);
                    System.out.println("---------------------------------------");
                    System.out.println("             " + month);
                    System.out.println("---------------------------------------");

                    // Calculate Weekly Pay
                    double monthlySalary = calculateWeeklyPay(attendanceSheet, employeeNumber, hourlyRate, df, month);

                    // Calculate Monthly Deductions and Net Pay
                    calculateDeductions(monthlySalary, df, monthlyBenefits);

                    return;
                }
            }

            if (!employeeFound) {
                System.out.println("Error: Employee Number " + employeeNumber + " not found. Please try again.");
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }

    public static double calculateWeeklyPay(Sheet attendanceSheet, int employeeNumber, double hourlyRate, DecimalFormat df, String month) {
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
                if (row.getRowNum() == 0) continue; // Skip header row

                // Retrieve employee number and date
                int empNum = (int) row.getCell(0).getNumericCellValue();
                LocalDate date = row.getCell(3).getLocalDateTimeCellValue().toLocalDate();

                if (empNum == employeeNumber && !date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                    // Retrieve log in and log out times
                    String clockInTime = getCellValueAsString(row.getCell(4)); // Log In (HH:mm)
                    String clockOutTime = getCellValueAsString(row.getCell(5)); // Log Out (HH:mm)

                    if (!clockInTime.isEmpty() && !clockOutTime.isEmpty()) {
                        try {
                            LocalTime clockIn = LocalTime.parse(clockInTime, DateTimeFormatter.ofPattern("HH:mm"));
                            LocalTime clockOut = LocalTime.parse(clockOutTime, DateTimeFormatter.ofPattern("HH:mm"));

                            LocalTime workStart = LocalTime.of(8, 0);
                            LocalTime workEnd = LocalTime.of(17, 0);
                            LocalTime lunchStart = LocalTime.of(12, 0);
                            LocalTime lunchEnd = LocalTime.of(13, 0);

                            if (clockIn.isAfter(workStart)) {
                                lateMinutes += workStart.until(clockIn, java.time.temporal.ChronoUnit.MINUTES);
                            }

                            LocalTime actualWorkStart = clockIn.isAfter(workStart) ? clockIn : workStart;
                            long morningMinutes = Math.max(0, actualWorkStart.until(lunchStart, java.time.temporal.ChronoUnit.MINUTES));
                            long afternoonMinutes = Math.max(0, lunchEnd.until(clockOut.isBefore(workEnd) ? clockOut : workEnd, java.time.temporal.ChronoUnit.MINUTES));

                            regularMinutes += (morningMinutes + afternoonMinutes);

                            if (!clockIn.isAfter(workStart) && clockOut.isAfter(workEnd)) {
                                long overtimeMinutes = workEnd.until(clockOut, java.time.temporal.ChronoUnit.MINUTES);
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

            // Print weekly summary
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
        LocalDate startDate = LocalDate.of(2024, 6, 3); // First working day of June
        LocalDate endDate = LocalDate.of(2024, 12, 31); // Last day of the year
        List<String[]> weeklyRanges = new ArrayList<>();

        // Generate weekly ranges dynamically from June 3 to December 31
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

        // Filter ranges by the given month
        List<String[]> filteredRanges = new ArrayList<>();
        for (String[] range : weeklyRanges) {
            LocalDate rangeStart = LocalDate.parse(range[0], DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            if (rangeStart.getMonth().toString().equalsIgnoreCase(month)) {
                filteredRanges.add(range);
            }
        }

        return filteredRanges;
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING -> {
                return cell.getStringCellValue();
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Format the cell as time
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                    return cell.getLocalDateTimeCellValue().toLocalTime().format(timeFormatter);
                } else {
                    return String.valueOf((long) cell.getNumericCellValue()); // Convert numeric to long for whole numbers
                }
            }
            case BOOLEAN -> {
                return String.valueOf(cell.getBooleanCellValue());
            }
            case FORMULA -> {
                return cell.getCellFormula();
            }
            default -> {
                return "";
            }
        }
    }

        private static void calculateDeductions(double monthlySalary, DecimalFormat df, double monthlyBenefits) {
        // SSS Contribution
        double sss = calculateSSS(monthlySalary);

        // PhilHealth Contribution
        double philHealth;
        if (monthlySalary <= 10000) {
            philHealth = 300.00; // Minimum contribution
        } else if (monthlySalary > 10000 && monthlySalary < 60000) {
            philHealth = monthlySalary * 0.03; // 3% of monthly salary
        } else {
            philHealth = 1800.00; // Maximum cap
        }
        double employeePhilHealthShare = philHealth / 2; // Only deduct employee share
        
        // Pag-IBIG Contribution
        double pagIbig;
        if (monthlySalary >= 1000 && monthlySalary <= 1500) {
            pagIbig = monthlySalary * 0.01; // Employee Share is 1% for salaries between 1,000 and 1,500
        } else if (monthlySalary > 1500) {
            pagIbig = Math.min(monthlySalary * 0.02, 100.00); // Employee Share is 2%, capped at 100
        } else {
            pagIbig = 0; // No contribution for salaries below 1,000
        }


        // Calculate Taxable Income (after deductions)
        double taxableIncome = monthlySalary - (sss + employeePhilHealthShare + pagIbig);

        // Withholding Tax Calculation
        double withholdingTax = calculateWithholdingTax(taxableIncome);

        // Net Pay Calculation
        double totalDeductions = sss + employeePhilHealthShare + pagIbig + withholdingTax;
        double netPay = (monthlySalary - totalDeductions) + monthlyBenefits; // Add benefits to net pay after deductions

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
        if (monthlySalary < 3250) {
            return 135.00;
        } else if (monthlySalary < 3750) {
            return 157.50;
        } else if (monthlySalary < 4250) {
            return 180.00;
        } else if (monthlySalary < 4750) {
            return 202.50;
        } else if (monthlySalary < 5250) {
            return 225.00;
        } else if (monthlySalary < 5750) {
            return 247.50;
        } else if (monthlySalary < 6250) {
            return 270.00;
        } else if (monthlySalary < 6750) {
            return 292.50;
        } else if (monthlySalary < 7250) {
            return 315.00;
        } else if (monthlySalary < 7750) {
            return 337.50;
        } else if (monthlySalary < 8250) {
            return 360.00;
        } else if (monthlySalary < 8750) {
            return 382.50;
        } else if (monthlySalary < 9250) {
            return 405.00;
        } else if (monthlySalary < 9750) {
            return 427.50;
        } else if (monthlySalary < 10250) {
            return 450.00;
        } else if (monthlySalary < 10750) {
            return 472.50;
        } else if (monthlySalary < 11250) {
            return 495.00;
        } else if (monthlySalary < 11750) {
            return 517.50;
        } else if (monthlySalary < 12250) {
            return 540.00;
        } else if (monthlySalary < 12750) {
            return 562.50;
        } else if (monthlySalary < 13250) {
            return 585.00;
        } else if (monthlySalary < 13750) {
            return 607.50;
        } else if (monthlySalary < 14250) {
            return 630.00;
        } else if (monthlySalary < 14750) {
            return 652.50;
        } else if (monthlySalary < 15250) {
            return 675.00;
        } else if (monthlySalary < 15750) {
            return 697.50;
        } else if (monthlySalary < 16250) {
            return 720.00;
        } else if (monthlySalary < 16750) {
            return 742.50;
        } else if (monthlySalary < 17250) {
            return 765.00;
        } else if (monthlySalary < 17750) {
            return 787.50;
        } else if (monthlySalary < 18250) {
            return 810.00;
        } else if (monthlySalary < 18750) {
            return 832.50;
        } else if (monthlySalary < 19250) {
            return 855.00;
        } else if (monthlySalary < 19750) {
            return 877.50;
        } else if (monthlySalary < 20250) {
            return 900.00;
        } else if (monthlySalary < 20750) {
            return 922.50;
        } else if (monthlySalary < 21250) {
            return 945.00;
        } else if (monthlySalary < 21750) {
            return 967.50;
        } else if (monthlySalary < 22250) {
            return 990.00;
        } else if (monthlySalary < 22750) {
            return 1012.50;
        } else if (monthlySalary < 23250) {
            return 1035.00;
        } else if (monthlySalary < 23750) {
            return 1057.50;
        } else if (monthlySalary < 24250) {
            return 1080.00;
        } else if (monthlySalary < 24750) {
            return 1102.50;
        } else {
            return 1125.00; // Maximum cap
        }
    }
    
    private static double calculateWithholdingTax(double taxableIncome) {
        double withholdingTax = 0;

        if (taxableIncome <= 20832) {
            withholdingTax = 0; // No tax for salaries ≤ 20,832
        } else if (taxableIncome > 20833 && taxableIncome <= 33333) {
            withholdingTax = (taxableIncome - 20833) * 0.20; // 20% for excess over 20,833
        } else if (taxableIncome > 33333 && taxableIncome <= 66667) {
            withholdingTax = 2500 + (taxableIncome - 33333) * 0.25; // Php 2,500 + 25% for excess over 33,333
        } else if (taxableIncome > 66667 && taxableIncome <= 166667) {
            withholdingTax = 10833 + (taxableIncome - 66667) * 0.30; // Php 10,833 + 30% for excess over 66,667
        } else if (taxableIncome > 166667 && taxableIncome <= 666667) {
            withholdingTax = 40833.33 + (taxableIncome - 166667) * 0.32; // Php 40,833.33 + 32% for excess over 166,667
        } else if (taxableIncome > 666667) {
            withholdingTax = 200833.33 + (taxableIncome - 666667) * 0.35; // Php 200,833.33 + 35% for excess over 666,667
        }

        return withholdingTax;
    }
}
