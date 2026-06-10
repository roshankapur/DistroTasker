const API_URL = 'http://localhost:8080/api/tasks';

// DOM Elements
const taskForm = document.getElementById('task-form');
const tasksTbody = document.getElementById('tasks-tbody');
const emptyState = document.getElementById('empty-state');
const connectionStatus = document.getElementById('connection-status');
const formAlert = document.getElementById('form-alert');
const toastContainer = document.getElementById('toast-container');
const refreshBtn = document.getElementById('refresh-btn');
const searchInput = document.getElementById('search-input');
const sortSelect = document.getElementById('sort-select');

// State
let tasks = [];
let pollInterval;
const POLL_RATE = 5000;

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    fetchTasks();
    startPolling();
});

// Event Listeners
taskForm.addEventListener('submit', submitTask);
refreshBtn.addEventListener('click', handleManualRefresh);
searchInput.addEventListener('input', renderTasks);
sortSelect.addEventListener('change', renderTasks);

// --- Core API Functions ---

async function fetchTasks() {
    try {
        const response = await fetch(API_URL);
        if (!response.ok) throw new Error('Network response was not ok');
        
        tasks = await response.json();
        setConnectionStatus(true);
        renderTasks();
    } catch (error) {
        console.error('Fetch error:', error);
        setConnectionStatus(false);
    }
}

async function submitTask(e) {
    e.preventDefault();
    hideFormAlert();

    const name = document.getElementById('taskName').value.trim();
    const scriptPath = document.getElementById('scriptPath').value.trim();
    const scheduledTime = document.getElementById('scheduledTime').value;

    const payload = {
        name,
        scriptPath,
        scheduledTime
    };

    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (response.status === 429) {
            const data = await response.json();
            showFormAlert(data.error || 'Rate Limit Exceeded', true);
            return;
        }

        if (!response.ok) throw new Error('Failed to schedule task');

        taskForm.reset();
        showToast('Task scheduled successfully!');
        
        // Instant sync after adding
        handleManualRefresh();
    } catch (error) {
        showFormAlert(error.message, true);
    }
}

async function deleteTask(id) {
    if (!confirm('Are you sure you want to delete this task?')) return;

    try {
        const response = await fetch(`${API_URL}/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Failed to delete task');
        
        showToast('Task deleted');
        // Instant sync after deletion
        handleManualRefresh();
    } catch (error) {
        showToast(error.message, true);
    }
}

// --- Polling Logic ---

function startPolling() {
    pollInterval = setInterval(fetchTasks, POLL_RATE);
}

function handleManualRefresh() {
    // Clear the interval so we don't get a "double flash" if the interval 
    // was about to fire right after our manual refresh.
    clearInterval(pollInterval);
    
    fetchTasks().then(() => {
        // Restart the polling cycle from 0
        startPolling();
        
        // Spin the icon for UX feedback
        const icon = refreshBtn.querySelector('ion-icon');
        icon.style.transition = 'transform 0.5s ease';
        icon.style.transform = `rotate(360deg)`;
        setTimeout(() => {
            icon.style.transition = 'none';
            icon.style.transform = `rotate(0deg)`;
        }, 500);
    });
}

// --- Rendering & Filtering ---

function renderTasks() {
    let filteredTasks = [...tasks];
    const searchTerm = searchInput.value.toLowerCase();

    // 1. Filter
    if (searchTerm) {
        filteredTasks = filteredTasks.filter(t => 
            (t.name && t.name.toLowerCase().includes(searchTerm)) ||
            (t.scriptPath && t.scriptPath.toLowerCase().includes(searchTerm)) ||
            (t.status && t.status.toLowerCase().includes(searchTerm))
        );
    }

    // 2. Sort
    const sortVal = sortSelect.value;
    filteredTasks.sort((a, b) => {
        const timeA = new Date(a.scheduledTime).getTime();
        const timeB = new Date(b.scheduledTime).getTime();
        
        if (sortVal === 'time-desc') return timeB - timeA;
        if (sortVal === 'time-asc') return timeA - timeB;
        if (sortVal === 'status') return a.status.localeCompare(b.status);
        if (sortVal === 'name') return (a.name || '').localeCompare(b.name || '');
        return 0;
    });

    // 3. Render
    tasksTbody.innerHTML = '';

    if (filteredTasks.length === 0) {
        emptyState.classList.remove('hidden');
    } else {
        emptyState.classList.add('hidden');
        filteredTasks.forEach(task => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${task.name || 'Unnamed Task'}</strong></td>
                <td class="code-font">${task.scriptPath}</td>
                <td>${formatDate(task.scheduledTime)}</td>
                <td><span class="badge status-${task.status.toLowerCase()}">${task.status}</span></td>
                <td>
                    <button class="btn-icon" onclick="deleteTask('${task.id}')" title="Delete">
                        <ion-icon name="trash-outline"></ion-icon>
                    </button>
                </td>
            `;
            tasksTbody.appendChild(tr);
        });
    }
}

// --- UI Utilities ---

function setConnectionStatus(isOnline) {
    if (isOnline) {
        connectionStatus.className = 'status-indicator online';
        connectionStatus.innerHTML = '<span class="dot"></span> Online';
    } else {
        connectionStatus.className = 'status-indicator offline';
        connectionStatus.innerHTML = '<span class="dot"></span> Offline';
    }
}

function showFormAlert(msg, isError = false) {
    formAlert.textContent = msg;
    formAlert.className = `alert ${isError ? 'alert-error' : ''}`;
    formAlert.classList.remove('hidden');
}

function hideFormAlert() {
    formAlert.classList.add('hidden');
}

function showToast(msg, isError = false) {
    const toast = document.createElement('div');
    toast.className = `toast ${isError ? 'error' : ''}`;
    toast.textContent = msg;
    
    toastContainer.appendChild(toast);
    
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%)';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

function formatDate(isoString) {
    const date = new Date(isoString);
    return date.toLocaleString(undefined, { 
        year: 'numeric', month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
}
