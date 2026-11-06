/**
 * VK Ad Blocker — Background Service Worker
 */

// Устанавливаем значения по умолчанию при установке
chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.local.set({ vkAdBlockerEnabled: true });
  updateBadgeState(true);
});

// Обрабатываем сообщения от content script
chrome.runtime.onMessage.addListener((message, sender) => {
  if (message.type === "updateBadge" && sender.tab) {
    const count = message.count;
    const text = count > 0 ? String(count) : "";
    chrome.action.setBadgeText({ text, tabId: sender.tab.id });
    chrome.action.setBadgeBackgroundColor({
      color: "#E64646",
      tabId: sender.tab.id,
    });
  }
});

// Обновляем состояние бейджа при изменении настроек
chrome.storage.onChanged.addListener((changes) => {
  if (changes.vkAdBlockerEnabled) {
    updateBadgeState(changes.vkAdBlockerEnabled.newValue);
  }
});

function updateBadgeState(enabled) {
  if (!enabled) {
    chrome.action.setBadgeText({ text: "OFF" });
    chrome.action.setBadgeBackgroundColor({ color: "#999999" });
  } else {
    chrome.action.setBadgeText({ text: "" });
  }
}
