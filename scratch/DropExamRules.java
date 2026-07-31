import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DropExamRules {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://103.75.182.249:5434/examination";
        String user = "postgres";
        String password = "K59Ptl*100504";
        
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("DROP TABLE IF EXISTS exam_rules CASCADE");
            System.out.println("SUCCESS: exam_rules table dropped.");
        }
    }
}
