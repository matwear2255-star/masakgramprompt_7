package edu.utem.ftmk.llm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // 1. Ensure the port is 3306 and the database name matches your phpMyAdmin exactly
    private static final String URL = "jdbc:mysql://localhost:3306/masakgramprompt"; 
    // 2. Default XAMPP user is root
    private static final String USER = "root"; 
    // 3. IMPORTANT: Clear this out so it is a blank string for XAMPP default config
    private static final String PASSWORD = ""; 
	
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}