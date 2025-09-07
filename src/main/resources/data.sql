-- Sample data for testing
INSERT INTO customers (customer_id, full_name, email, phone) VALUES 
(1, 'John Doe', 'john.doe@email.com', '555-0101'),
(2, 'Jane Smith', 'jane.smith@email.com', '555-0102'),
(3, 'Bob Johnson', 'bob.johnson@email.com', '555-0103');

INSERT INTO accounts (account_id, customer_id, account_type, balance, created_at) VALUES 
(101, 1, 'CHECKING', 1500.00, CURRENT_TIMESTAMP),
(102, 1, 'SAVINGS', 5000.00, CURRENT_TIMESTAMP),
(103, 2, 'CHECKING', 2500.00, CURRENT_TIMESTAMP),
(104, 3, 'SAVINGS', 10000.00, CURRENT_TIMESTAMP);

INSERT INTO transactions (transaction_id, account_id, transaction_date, amount, type, description) VALUES 
(1001, 101, CURRENT_TIMESTAMP, 100.00, 'DEPOSIT', 'Initial deposit'),
(1002, 101, CURRENT_TIMESTAMP, -50.00, 'WITHDRAWAL', 'ATM withdrawal'),
(1003, 102, CURRENT_TIMESTAMP, 2000.00, 'DEPOSIT', 'Salary deposit'),
(1004, 103, CURRENT_TIMESTAMP, 500.00, 'DEPOSIT', 'Initial deposit'),
(1005, 104, CURRENT_TIMESTAMP, 10000.00, 'DEPOSIT', 'Initial deposit');

INSERT INTO loans (loan_id, customer_id, amount, status, start_date, end_date) VALUES 
(2001, 1, 15000.00, 'ACTIVE', CURRENT_TIMESTAMP, DATEADD('YEAR', 5, CURRENT_TIMESTAMP)),
(2002, 2, 25000.00, 'PENDING', CURRENT_TIMESTAMP, DATEADD('YEAR', 7, CURRENT_TIMESTAMP)),
(2003, 3, 50000.00, 'PAID_OFF', CURRENT_TIMESTAMP, DATEADD('YEAR', 3, CURRENT_TIMESTAMP));
