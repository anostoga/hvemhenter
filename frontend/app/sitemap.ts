import type { MetadataRoute } from "next";

const baseUrl = "https://www.hvemhenter.no";

export default function sitemap(): MetadataRoute.Sitemap {
  return [
    {
      url: baseUrl,
      changeFrequency: "weekly",
      priority: 1,
    },
    {
      url: `${baseUrl}/om`,
      changeFrequency: "monthly",
      priority: 0.7,
    },
    {
      url: `${baseUrl}/personvern`,
      changeFrequency: "yearly",
      priority: 0.3,
    },
  ];
}
