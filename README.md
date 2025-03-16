# MotorPH Payroll System

The MotorPH Payroll System is a Java-based application designed to simplify payroll management for MotorPH. It leverages advanced features like Excel integration to handle employee details and attendance records, enabling accurate computation of salaries, deductions, and benefits.

## Features
- Computes monthly salaries dynamically based on attendance records.
- Calculates government-mandated deductions: SSS, PhilHealth, Pag-IBIG, and withholding tax.
- Incorporates monthly benefits, such as:
  - Rice subsidy
  - Phone allowance
  - Clothing allowance
- Displays detailed payroll summaries, including:
  - Employee details (Name, Birthday, etc.)
  - Weekly salary breakdown
  - Total deductions
  - Monthly benefits
  - Final net pay

## System Requirements
- **Java 11** or higher
- Apache POI library for Excel integration

## Getting Started
1. Clone this repository:
   bash
   git clone (https://github.com/vincentkian/MO-IT101-Group7)
   
2. Ensure the required dependencies (e.g., Apache POI) are added to your project.
3. Place the source file `MotorPH_Employee_Data.xlsx` in the `src` directory:
   
   src/MotorPH_Employee_Data.xlsx
   
   This file contains:
   - **Employee Details**: Information such as hourly rates and benefits.
   - **Attendance Record**: Data used to calculate weekly and monthly pay.

4. Compile and run the program:
   bash
   javac -cp .;path_to_poi_library/* com/motorph/motorphpayrollsystem/MotorPHPayrollSystem.java
   java -cp .;path_to_poi_library/* com.motorph.motorphpayrollsystem.MotorPHPayrollSystem

## File Structure
- src: Contains source files, including `MotorPH_Employee_Data.xlsx`.
- MotorPHPayrollSystem.java: The main entry point of the application.

## How It Works
1. The user provides the **Employee Number** and selects the month to compute payroll.
2. The system reads employee details and attendance from the Excel file (`MotorPH_Employee_Data.xlsx`).
3. The following calculations are performed:
   - **Salary**: Based on hours worked (including overtime).
   - **Deductions**: Includes SSS, PhilHealth, Pag-IBIG, and withholding tax.
   - **Benefits**: Adds non-taxable allowances (rice subsidy, phone, and clothing).
4. A detailed payroll summary is printed to the console.

## Limitations
- The program currently only reads data from the provided Excel file format.
