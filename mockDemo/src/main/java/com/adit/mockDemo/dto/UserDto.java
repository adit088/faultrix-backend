package com.adit.mockDemo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;



@Getter                 // we use getter here because @data generates setters to which can disrupt dto mutability!
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private long id;
    private String empName;
    private String empRole;
    private double empSalary;

}
