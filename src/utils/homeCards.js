export const defaultHomeCards = {
  about: {
    title: "关于我",
    subtitle: "ABOUT ME",
    paragraphs: [
      "这里写你的个人介绍，可以稍微长一点。",
      "例如技术方向、学习经历、博客定位和你正在关注的内容。",
    ],
    items: [],
  },
  workExp: {
    title: "工作履历",
    subtitle: "CAREER",
    paragraphs: [
      "这里写项目经历、实习经历或学习实践。",
      "可以按时间顺序简要描述重点。",
    ],
    items: [],
  },
  contact: {
    title: "联系我",
    subtitle: "CONTACT ME",
    paragraphs: [],
    items: [
      "Email: example@example.com",
      "GitHub: github.com/example",
      "QQ / WeChat: 按需填写",
    ],
  },
};

const textArray = value => {
  if (!Array.isArray(value)) {
    return [];
  }

  return value
    .filter(item => typeof item === "string")
    .map(item => item.trim())
    .filter(Boolean);
};

const textValue = (value, fallback) => {
  if (typeof value !== "string" || !value.trim()) {
    return fallback;
  }

  return value.trim();
};

const normalizeCard = (card, fallback) => {
  const paragraphs = textArray(card?.paragraphs);
  const items = textArray(card?.items);
  const hasContent = paragraphs.length > 0 || items.length > 0;

  return {
    title: textValue(card?.title, fallback.title),
    subtitle: textValue(card?.subtitle, fallback.subtitle),
    paragraphs: hasContent ? paragraphs : fallback.paragraphs,
    items: hasContent ? items : fallback.items,
  };
};

export const normalizeHomeCards = cards => ({
  about: normalizeCard(cards?.about, defaultHomeCards.about),
  workExp: normalizeCard(cards?.workExp, defaultHomeCards.workExp),
  contact: normalizeCard(cards?.contact, defaultHomeCards.contact),
});

export const loadHomeCards = async () => {
  try {
    const response = await fetch(`${import.meta.env.BASE_URL}home-cards.json`, {
      cache: "no-store",
    });

    if (!response.ok) {
      throw new Error(`Load home cards failed: ${response.status}`);
    }

    return normalizeHomeCards(await response.json());
  } catch (error) {
    console.error("Load home cards failed, use defaults:", error);
    return normalizeHomeCards(defaultHomeCards);
  }
};
