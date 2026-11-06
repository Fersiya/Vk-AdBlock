/**
 * VK Ad Blocker — Content Script
 * Удаляет рекламные элементы со страниц ВКонтакте
 */

(function () {
  "use strict";

  // Проверяем, включено ли расширение
  let isEnabled = true;

  chrome.storage.local.get("vkAdBlockerEnabled", (data) => {
    isEnabled = data.vkAdBlockerEnabled !== false; // По умолчанию включено
    if (isEnabled) {
      init();
    }
  });

  // Слушаем изменения настроек
  chrome.storage.onChanged.addListener((changes) => {
    if (changes.vkAdBlockerEnabled) {
      isEnabled = changes.vkAdBlockerEnabled.newValue;
      if (isEnabled) {
        removeAds();
      }
    }
  });

  // =====================
  // Селекторы рекламных элементов
  // =====================
  const AD_SELECTORS = [
    // Промо-посты в ленте
    ".wall_marked_as_ads",
    ".post.marked_as_ads",
    ".feed_row.ads_promoted_post",
    "._ads_block_data_w",
    ".ads_promoted_post",
    '[data-ad="true"]',
    "[data-ad-view]",
    "[data-ad-data]",
    ".Post--ad",
    ".FeedBlock--ads",
    ".feed_ads",

    // Баннеры
    "#ads_left",
    "#ads_right",
    ".ads_block",
    ".ads_rotate_container",
    "#ads_rotating",
    ".ads_ad_box",
    ".ads_targ_block",
    ".aside_ads",
    ".ads_cont",
    ".ads_promo",
    ".ui_rmenu_pr",
    ".side_block_ads",

    // Stories
    ".stories_feed_promo",
    ".stories_ads_wrap",
    ".StoriesViewer__ad",
    ".stories_ad",
    ".stories_promo",
    ".StoriesFeedBanner",
    ".stories_feed_preview--ad",

    // Промо
    ".top_profile_mrow.promo",
    ".top_nav_promo",
    ".IndexPromo",
    ".index_promo",
    ".TopBanner",
    ".TopPromo",
    ".MovieBanner",
    ".BannerInFeed",
    ".FeedBanner",

    // Рекомендуемые группы (рекламные)
    ".feed_groups_recomm",
    ".groups_recomm",
    ".group_row_promoted",
    ".CommunityRecommendation--ad",
    ".friends_recomm_block",

    // Видео
    ".videoplayer_ad",
    ".videoplayer_ad_overlay",
    ".vv_ad_block",
    ".video_box_ad",

    // Промо-блоки
    ".miniapps_promo",
    ".MiniAppsPromoBlock",
    ".CatalogBanner",
    ".market_promo_block",
    ".DonutPromo",
    ".VkPayPromo",
    ".AudioSubscriptionPromo",
    ".MusicSubscriptionPromo",

    // Клипы
    ".clips_ad",
    ".ClipsAd",
    ".clips_item--ad",

    // Новый дизайн (VKUI)
    ".vkuiAdvertising",
    ".vkuiBanner--ad",
    ".vkuiPromoBlock",
  ];

  // Атрибуты, по которым определяем рекламу
  const AD_ATTRIBUTES = [
    "data-ad",
    "data-ad-view",
    "data-ad-data",
    "data-ad-id",
  ];

  // Текстовые маркеры рекламы
  const AD_TEXT_MARKERS = ["Реклама", "Рекламная запись", "Sponsored"];

  let removedCount = 0;
  let observer = null;

  // =====================
  // Основные функции
  // =====================

  function init() {
    // Удаляем существующую рекламу при загрузке
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", () => {
        removeAds();
        startObserver();
      });
    } else {
      removeAds();
      startObserver();
    }
  }

  /**
   * Удаляет все найденные рекламные элементы
   */
  function removeAds() {
    if (!isEnabled) return;

    // 1. Удаление по CSS-селекторам
    const selectorString = AD_SELECTORS.join(", ");
    const adElements = document.querySelectorAll(selectorString);
    adElements.forEach((el) => hideElement(el));

    // 2. Удаление по атрибутам
    AD_ATTRIBUTES.forEach((attr) => {
      document.querySelectorAll(`[${attr}]`).forEach((el) => {
        // Проверяем, что это действительно рекламный элемент
        if (isAdElement(el)) {
          hideElement(el);
        }
      });
    });

    // 3. Поиск промо-постов по тексту
    removeAdsByText();

    // 4. Удаление рекламных iframe
    removeAdIframes();

    // 5. Обновляем счётчик
    updateBadge();
  }

  /**
   * Проверяет, является ли элемент рекламным
   */
  function isAdElement(el) {
    // Проверяем классы
    const className = el.className || "";
    if (typeof className === "string") {
      if (
        className.includes("ads_") ||
        className.includes("_ads") ||
        className.includes("promo") ||
        className.includes("ad_") ||
        className.includes("--ad")
      ) {
        return true;
      }
    }

    // Проверяем атрибуты
    for (const attr of AD_ATTRIBUTES) {
      if (el.hasAttribute(attr)) return true;
    }

    return false;
  }

  /**
   * Ищет посты с текстовой меткой "Реклама"
   */
  function removeAdsByText() {
    // Ищем записи на стене
    const posts = document.querySelectorAll(
      ".wall_item, .post, .feed_row, ._post"
    );
    posts.forEach((post) => {
      // Ищем метку рекламы внутри поста
      const labels = post.querySelectorAll(
        ".wall_marked_as_ads_label, .PostHeaderSubtitle__item, .PostAdLabel, .ads_label, .post_ad_mark"
      );
      labels.forEach((label) => {
        const text = label.textContent.trim();
        if (AD_TEXT_MARKERS.some((marker) => text.includes(marker))) {
          hideElement(post);
        }
      });

      // Дополнительная проверка через data-атрибуты
      if (
        post.dataset.ad === "true" ||
        post.querySelector("[data-ad-view]") ||
        post.classList.contains("ads_promoted_post") ||
        post.classList.contains("wall_marked_as_ads")
      ) {
        hideElement(post);
      }
    });
  }

  /**
   * Удаляет рекламные iframe
   */
  function removeAdIframes() {
    document.querySelectorAll("iframe").forEach((iframe) => {
      const src = iframe.src || "";
      if (
        src.includes("ads.vk.com") ||
        src.includes("ad.mail.ru") ||
        src.includes("target.my.com") ||
        src.includes("top.mail.ru") ||
        src.includes("/ads/")
      ) {
        hideElement(iframe);
      }
    });
  }

  /**
   * Скрывает элемент
   */
  function hideElement(el) {
    if (el && !el.dataset.vkAdBlockerHidden) {
      el.style.setProperty("display", "none", "important");
      el.style.setProperty("visibility", "hidden", "important");
      el.style.setProperty("height", "0", "important");
      el.style.setProperty("overflow", "hidden", "important");
      el.style.setProperty("margin", "0", "important");
      el.style.setProperty("padding", "0", "important");
      el.style.setProperty("opacity", "0", "important");
      el.style.setProperty("pointer-events", "none", "important");
      el.dataset.vkAdBlockerHidden = "true";
      removedCount++;
    }
  }

  /**
   * Обновляет счётчик на иконке расширения
   */
  function updateBadge() {
    try {
      chrome.runtime.sendMessage({
        type: "updateBadge",
        count: removedCount,
      });
    } catch (e) {
      // Расширение могло быть перезагружено
    }
  }

  // =====================
  // MutationObserver — отслеживание новых элементов
  // =====================

  function startObserver() {
    if (observer) observer.disconnect();

    observer = new MutationObserver((mutations) => {
      if (!isEnabled) return;

      let needsScan = false;

      for (const mutation of mutations) {
        if (mutation.type === "childList" && mutation.addedNodes.length > 0) {
          for (const node of mutation.addedNodes) {
            if (node.nodeType === Node.ELEMENT_NODE) {
              // Быстрая проверка добавленного узла
              if (isAdElement(node)) {
                hideElement(node);
              } else {
                // Проверяем потомков
                needsScan = true;
              }
            }
          }
        }
      }

      if (needsScan) {
        // Используем requestIdleCallback для оптимизации производительности
        if (window.requestIdleCallback) {
          requestIdleCallback(() => removeAds(), { timeout: 500 });
        } else {
          setTimeout(removeAds, 100);
        }
      }
    });

    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
    });
  }

  // Периодическая проверка (на случай динамической подгрузки)
  setInterval(() => {
    if (isEnabled) {
      removeAds();
    }
  }, 3000);
})();
