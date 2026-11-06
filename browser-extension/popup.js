/**
 * VK Ad Blocker — Popup Script
 */

const toggle = document.getElementById("toggleEnabled");
const statusBar = document.getElementById("statusBar");
const blockedCount = document.getElementById("blockedCount");

// Загружаем текущее состояние
chrome.storage.local.get(
  ["vkAdBlockerEnabled", "vkAdBlockerCount"],
  (data) => {
    const enabled = data.vkAdBlockerEnabled !== false;
    toggle.checked = enabled;
    updateStatusUI(enabled);
    blockedCount.textContent = data.vkAdBlockerCount || 0;
  }
);

// Переключатель
toggle.addEventListener("change", () => {
  const enabled = toggle.checked;
  chrome.storage.local.set({ vkAdBlockerEnabled: enabled });
  updateStatusUI(enabled);
});

function updateStatusUI(enabled) {
  if (enabled) {
    statusBar.textContent = "✅ Защита активна";
    statusBar.className = "status-bar active";
  } else {
    statusBar.textContent = "⛔ Защита отключена";
    statusBar.className = "status-bar inactive";
  }
}

// Обновляем счётчик заблокированных элементов
chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
  if (tabs[0]) {
    chrome.action.getBadgeText({ tabId: tabs[0].id }, (text) => {
      if (text && text !== "OFF") {
        blockedCount.textContent = text;
      }
    });
  }
});
