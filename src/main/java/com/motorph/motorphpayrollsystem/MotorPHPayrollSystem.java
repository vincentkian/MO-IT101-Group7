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

    	System.out.print("Enter Employee Number: ");
    	int employeeNumber = scanner.nextInt();
    	scanner.nextLine();

    	String month;
    	do {
        	System.out.print("Enter the month to display: ");
        	month = scanner.nextLine();
    	} while (getDateRangeForMonth(month).isEmpty());

    	String filePath = "src/MotorPH_Employee_Data.xlsx";
    	displayEmployeePayroll(filePath, employeeNumber, month);
	}

	public static void displayEmployeePayroll(String filePath, int employeeNumber, String month) {
    	try (FileInputStream fis = new FileInputStream(new File(filePath));
         	Workbook workbook = new XSSFWorkbook(fis)) {

        	Sheet employeeSheet = workbook.getSheet("Employee Details");
        	Sheet attendanceSheet = workbook.getSheet("Attendance Record");

        	if (employeeSheet == null || attendanceSheet == null) {
            	System.out.println("Error: Required sheets not found in the Excel file.");
            	return;
        	}

        	EmployeeDetails employeeDetails = getEmployeeDetails(employeeSheet, employeeNumber);
        	if (employeeDetails == null) {
            	System.out.println("Error: Employee Number " + employeeNumber + " not found.");
            	return;
        	}

        	displayEmployeeHeader(employeeDetails, month);
        	double monthlySalary = calculateMonthlySalary(attendanceSheet, employeeNumber, employeeDetails.getHourlyRate(), month);
        	calculateDeductions(monthlySalary, employeeDetails.getMonthlyBenefits());
    	} catch (IOException e) {
        	System.out.println("Error reading file: " + e.getMessage());
    	}
	}

	private static EmployeeDetails getEmployeeDetails(Sheet employeeSheet, int employeeNumber) {
    	for (Row row : employeeSheet) {
        	Cell employeeCell = row.getCell(0);

        	if (employeeCell != null && getCellValueAsString(employeeCell).trim().equals(String.valueOf(employeeNumber).trim())) {
            	String firstName = getCellValueAsString(row.getCell(2));
            	String lastName = getCellValueAsString(row.getCell(1));
            	String birthday = row.getCell(3).getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            	double hourlyRate = row.getCell(18).getNumericCellValue();

            	// Sum all benefits (rice, phone, clothing allowances)
            	double riceSubsidy = row.getCell(14) != null ? row.getCell(14).getNumericCellValue() : 0;
            	double phoneAllowance = row.getCell(15) != null ? row.getCell(15).getNumericCellValue() : 0;
            	double clothingAllowance = row.getCell(16) != null ? row.getCell(16).getNumericCellValue() : 0;
            	double monthlyBenefits = riceSubsidy + phoneAllowance + clothingAllowance;

            	return new EmployeeDetails(employeeNumber, firstName, lastName, birthday, hourlyRate, monthlyBenefits);
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
    	System.out.println("         	" + month);
    	System.out.println("---------------------------------------");
	}

	private static double calculateMonthlySalary(Sheet attendanceSheet, int employeeNumber, double hourlyRate, String month) {
    	double totalMonthlyPay = 0;
    	// Overtime is calculated at 25% higher than regular rate
    	double overtimeRate = hourlyRate * 1.25;

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
            	if (row.getRowNum() == 0) continue;

            	int currentEmployeeNumber = (int) row.getCell(0).getNumericCellValue();
            	LocalDate date = row.getCell(3).getLocalDateTimeCellValue().toLocalDate();

            	if (currentEmployeeNumber == employeeNumber && !date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                	String logInTime = getCellValueAsString(row.getCell(4));
                	String logOutTime = getCellValueAsString(row.getCell(5));

                	if (!logInTime.isEmpty() && !logOutTime.isEmpty()) {
                    	try {
                        	LocalTime logIn = LocalTime.parse(logInTime, DateTimeFormatter.ofPattern("HH:mm"));
                        	LocalTime logOut = LocalTime.parse(logOutTime, DateTimeFormatter.ofPattern("HH:mm"));

                        	// Standard work hours: 8AM-5PM with 1-hour lunch break
                        	LocalTime workStart = LocalTime.of(8, 0);
                        	LocalTime workEnd = LocalTime.of(17, 0);
                        	LocalTime lunchStart = LocalTime.of(12, 0);
                        	LocalTime lunchEnd = LocalTime.of(13, 0);

                        	// Late calculation: minutes after 8AM
                        	if (logIn.isAfter(workStart)) {
                            	lateMinutes += workStart.until(logIn, java.time.temporal.ChronoUnit.MINUTES);
                        	}

                        	// Regular hours calculation:
                        	// Morning work (from logIn or 8AM to 12PM)
                        	LocalTime actualWorkStart = logIn.isAfter(workStart) ? logIn : workStart;
                        	long morningMinutes = Math.max(0, actualWorkStart.until(lunchStart, java.time.temporal.ChronoUnit.MINUTES));
                       	 
                        	// Afternoon work (from 1PM to logOut or 5PM)
                        	long afternoonMinutes = Math.max(0, lunchEnd.until(logOut.isBefore(workEnd) ? logOut : workEnd, java.time.temporal.ChronoUnit.MINUTES));

                        	regularMinutes += (morningMinutes + afternoonMinutes);

                        	// Overtime calculation: work after 5PM
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

        	// Convert minutes to hours for pay calculation
        	weeklyRegularPay = (regularMinutes / 60.0) * hourlyRate;
        	double weeklySalary = weeklyRegularPay + weeklyOvertimePay;
        	totalMonthlyPay += weeklySalary;

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

	private static void calculateDeductions(double monthlySalary, double monthlyBenefits) {
    	DecimalFormat df = new DecimalFormat("#,##0.00");

    	// PhilHealth: 3% of salary, min 300, max 1800 (employee share is half)
    	double philHealth = Math.min(Math.max(monthlySalary * 0.03, 300), 1800) / 2;

    	// Pag-IBIG: 1% if salary 1k-1.5k, 2% otherwise (max 100)
    	double pagIbig = monthlySalary >= 1000 ?
                    	(monthlySalary <= 1500 ? monthlySalary * 0.01 : Math.min(monthlySalary * 0.02, 100)) :
                    	0;

    	// SSS uses government-mandated bracket system
    	double sss = calculateSSS(monthlySalary);

    	// Taxable income is after SSS, PhilHealth, Pag-IBIG deductions
    	double taxableIncome = monthlySalary - (sss + philHealth + pagIbig);
   	 
    	// Withholding tax uses progressive tax brackets
    	double withholdingTax = calculateWithholdingTax(taxableIncome);

    	double totalDeductions = sss + philHealth + pagIbig + withholdingTax;
    	double netPay = (monthlySalary - totalDeductions) + monthlyBenefits;

    	System.out.println("Deductions:");
    	System.out.println("SSS: Php " + df.format(sss));
    	System.out.println("PhilHealth: Php " + df.format(philHealth));
    	System.out.println("Pag-IBIG: Php " + df.format(pagIbig));
    	System.out.println("Withholding Tax: Php " + df.format(withholdingTax));
    	System.out.println("Monthly Benefits: Php " + df.format(monthlyBenefits));
    	System.out.println("Net Pay: Php " + df.format(netPay));
	}

	// SSS uses a government-defined bracket system where each salary range has a fixed contribution
	private static double calculateSSS(double monthlySalary) {
    	NavigableMap<Double, Double> sssTable = new TreeMap<>();
    	// Bracket values and corresponding contributions
    	double[] salaryBrackets = {3250, 3750, 4250, 4750, 5250, 5750, 6250, 6750, 7250, 7750,
                             	8250, 8750, 9250, 9750, 10250, 10750, 11250, 11750, 12250, 12750,
                             	13250, 13750, 14250, 14750, 15250, 15750, 16250, 16750, 17250, 17750,
                             	18250, 18750, 19250, 19750, 20250, 20750, 21250, 21750, 22250, 22750,
                             	23250, 23750, 24250, 24750};
    	double[] sssContributions = {135, 157.5, 180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5,
                               	360, 382.5, 405, 427.5, 450, 472.5, 495, 517.5, 540, 562.5,
                               	585, 607.5, 630, 652.5, 675, 697.5, 720, 742.5, 765, 787.5,
                               	810, 832.5, 855, 877.5, 900, 922.5, 945, 967.5, 990, 1012.5,
                               	1035, 1057.5, 1080, 1102.5};

    	for (int i = 0; i < salaryBrackets.length; i++) {
        	sssTable.put(salaryBrackets[i], sssContributions[i]);
    	}

    	if (monthlySalary <= 0) return 0.0;
    	if (monthlySalary < sssTable.firstKey()) return sssTable.get(sssTable.firstKey());
   	 
    	Double key = sssTable.ceilingKey(monthlySalary);
    	return key != null ? sssTable.get(key) : sssTable.get(sssTable.lastKey());
	}

	// Philippine progressive tax computation (2024 brackets)
	private static double calculateWithholdingTax(double taxableIncome) {
    	if (taxableIncome <= 20832) return 0;
    	else if (taxableIncome <= 33333) return (taxableIncome - 20833) * 0.20;
    	else if (taxableIncome <= 66667) return 2500 + (taxableIncome - 33333) * 0.25;
    	else if (taxableIncome <= 166667) return 10833 + (taxableIncome - 66667) * 0.30;
    	else if (taxableIncome <= 666667) return 40833.33 + (taxableIncome - 166667) * 0.32;
    	else return 200833.33 + (taxableIncome - 666667) * 0.35;
	}

	private static String getCellValueAsString(Cell cell) {
    	if (cell == null) return "";

    	switch (cell.getCellType()) {
        	case STRING: return cell.getStringCellValue();
        	case NUMERIC:
            	if (DateUtil.isCellDateFormatted(cell)) {
                	DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                	return cell.getLocalDateTimeCellValue().toLocalTime().format(timeFormatter);
            	} else {
                	return String.valueOf((long) cell.getNumericCellValue());
            	}
        	case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
        	case FORMULA: return cell.getCellFormula();
        	default: return "";
    	}
	}

	// Generates weekly date ranges for payroll processing (Monday-Sunday weeks)
	private static List<String[]> getDateRangeForMonth(String month) {
    	LocalDate startDate = LocalDate.of(2024, 6, 3);
    	LocalDate endDate = LocalDate.of(2024, 12, 31);
    	List<String[]> weeklyRanges = new ArrayList<>();

    	LocalDate weekStart = startDate;
    	while (!weekStart.isAfter(endDate)) {
        	LocalDate weekEnd = weekStart.plusDays(6);
        	if (weekEnd.isAfter(endDate)) weekEnd = endDate;

        	weeklyRanges.add(new String[]{
            	weekStart.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
            	weekEnd.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))
        	});

        	weekStart = weekStart.plusDays(7);
    	}

    	List<String[]> filteredRanges = new ArrayList<>();
    	for (String[] range : weeklyRanges) {
        	LocalDate rangeStart = LocalDate.parse(range[0], DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        	if (rangeStart.getMonth().toString().equalsIgnoreCase(month)) {
            	filteredRanges.add(range);
        	}
    	}

    	return filteredRanges;
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

    	public int getEmployeeNumber() { return employeeNumber; }
    	public String getFirstName() { return firstName; }
    	public String getLastName() { return lastName; }
    	public String getBirthday() { return birthday; }
    	public double getHourlyRate() { return hourlyRate; }
    	public double getMonthlyBenefits() { return monthlyBenefits; }
	}
}
