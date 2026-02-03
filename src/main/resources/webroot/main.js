const API_URL = "http://localhost:8080/api";
let token = localStorage.getItem("token");

if(token) initDashboard();

async function login() {
    const id = document.getElementById("login-id").value;
    const pass = document.getElementById("login-pass").value;
    const errDisplay = document.getElementById("error-msg");

    try {
        const res = await fetch(`${API_URL}/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ employee_id: id, password: pass })
        });

        if(res.ok) {
            const data = await res.json();
            token = data.token;
            localStorage.setItem("token", token);
            errDisplay.innerText = "";
            initDashboard();
        } else {
            errDisplay.innerText = "Invalid Credentials";
        }
    } catch (e) {
        console.error(e);
        errDisplay.innerText = "Server Error";
    }
}

function initDashboard() {
    document.getElementById("login-section").classList.add("hidden");
    document.getElementById("dashboard-section").classList.remove("hidden");
    fetchProfile(); // New!
    fetchBalance();
    fetchHistory();
}

// --- NEW: FETCH PROFILE ---
async function fetchProfile() {
    const res = await fetch(`${API_URL}/v1/profile`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const data = await res.json();
        // Update Welcome Header
        document.getElementById("welcome-msg").innerText = `Welcome, ${data.first_name}`;

        // Update Profile Card
        document.getElementById("p-name").innerText = `${data.first_name} ${data.last_name}`;
        document.getElementById("p-id").innerText = data.employee_id;
        document.getElementById("p-email").innerText = data.email;
        document.getElementById("p-role").innerText = data.role;
    }
}

async function fetchBalance() {
    const res = await fetch(`${API_URL}/v1/balance`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const data = await res.json();
        document.getElementById("balance-display").innerText =  data.balance.toFixed(2)+"Rs";
    }
}

async function fetchHistory() {
    const res = await fetch(`${API_URL}/v1/history`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const data = await res.json();
        const tbody = document.getElementById("history-table");
        tbody.innerHTML = "";

        if(data.history.length === 0) {
            tbody.innerHTML = "<tr><td colspan='4' style='text-align:center; color:#999;'>No transactions yet</td></tr>";
            return;
        }

        data.history.forEach(tx => {
            const badgeClass = tx.type === 'CREDIT' ? 'badge-credit' : 'badge-debit';
            const sign = tx.type === 'CREDIT' ? '+' : '-';

            const row = `<tr>
                    <td><span class="${badgeClass}">${tx.type}</span></td>
                    <td><b>${sign}${tx.amount.toFixed(2)}Rs.</b></td>
                    <td style="color: #666; font-size: 0.85rem;">${tx.ref_id}</td>
                    <td style="color: #888; font-size: 0.85rem;">${new Date(tx.date).toLocaleDateString()}</td>
                </tr>`;
            tbody.innerHTML += row;
        });
    }
}

async function transfer() {
    const to = document.getElementById("to-email").value;
    const amount = parseFloat(document.getElementById("amount").value);
    const msg = document.getElementById("transfer-msg");

    msg.innerText = "Sending...";
    msg.className = "";

    const res = await fetch(`${API_URL}/v1/transfer`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Authorization": `Bearer ${token}` },
        body: JSON.stringify({ to: to, amount: amount, ref_id: "web-" + Date.now() })
    });

    if(res.ok) {
        msg.innerText = "Transfer Successful!";
        msg.className = "success";
        fetchBalance();
        fetchHistory();
        // Clear inputs
        document.getElementById("to-email").value = "";
        document.getElementById("amount").value = "";
    } else {
        const err = await res.json();
        msg.innerText = "Error: " + (err.error || "Failed");
        msg.className = "error";
    }
}

function logout() {
    localStorage.removeItem("token");
    location.reload();
}