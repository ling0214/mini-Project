package com.miniproject.backend.integrations;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the HTML parsing logic against a real page-structure fixture
 * (trimmed from a live github.com/trending fetch, not fabricated) rather
 * than hitting the network in CI. See fetchTop()'s regexes -- this is the
 * fragile part flagged in the implementation plan.
 */
class GitHubTrendingFetcherTest {

    // Trimmed excerpt of the real github.com/trending markup structure (two article blocks).
    private static final String SAMPLE_HTML = """
            <article class="Box-row">
              <h2 class="h3 lh-condensed">
                <a href="/TencentCloud/TencentDB-Agent-Memory" data-view-component="true" class="Link">
                  <span class="text-normal">TencentCloud /</span>
                  TencentDB-Agent-Memory</a>
              </h2>
              <p class="col-9 color-fg-muted my-1 tmp-pr-4">
                TencentDB Agent Memory is a team-level memory hub for AI Agents.
              </p>
              <div class="f6 color-fg-muted mt-2">
                <a href="/TencentCloud/TencentDB-Agent-Memory/stargazers" data-view-component="true" class="tmp-mr-3 Link Link--muted d-inline-block"><svg><path></path></svg>
                  13,943</a>
              </div>
            </article>
            <article class="Box-row">
              <h2 class="h3 lh-condensed">
                <a href="/someorg/some-repo" data-view-component="true" class="Link">
                  <span class="text-normal">someorg /</span>
                  some-repo</a>
              </h2>
              <p class="col-9 color-fg-muted my-1 tmp-pr-4">
                A short description of some repo.
              </p>
              <div class="f6 color-fg-muted mt-2">
                <a href="/someorg/some-repo/stargazers" data-view-component="true" class="tmp-mr-3 Link Link--muted d-inline-block"><svg><path></path></svg>
                  501</a>
              </div>
            </article>
            """;

    @Test
    void parsesRepoNameDescriptionAndStarsFromRealPageStructure() throws Exception {
        GitHubTrendingFetcher fetcher = new GitHubTrendingFetcher();
        List<GitHubTrendingFetcher.TrendingRepo> repos = parseHtml(fetcher, SAMPLE_HTML, 3);

        assertThat(repos).hasSize(2);
        assertThat(repos.get(0).fullName()).isEqualTo("TencentCloud/TencentDB-Agent-Memory");
        assertThat(repos.get(0).description()).isEqualTo("TencentDB Agent Memory is a team-level memory hub for AI Agents.");
        assertThat(repos.get(0).stars()).isEqualTo("13,943");
        assertThat(repos.get(1).fullName()).isEqualTo("someorg/some-repo");
        assertThat(repos.get(1).stars()).isEqualTo("501");
    }

    @Test
    void respectsLimit() throws Exception {
        GitHubTrendingFetcher fetcher = new GitHubTrendingFetcher();
        List<GitHubTrendingFetcher.TrendingRepo> repos = parseHtml(fetcher, SAMPLE_HTML, 1);

        assertThat(repos).hasSize(1);
    }

    /** fetchTop() does an HTTP call before parsing -- reflection lets us test just the regex parsing against a fixture. */
    private List<GitHubTrendingFetcher.TrendingRepo> parseHtml(GitHubTrendingFetcher fetcher, String html, int limit) throws Exception {
        Method parse = GitHubTrendingFetcher.class.getDeclaredMethod("parseHtml", String.class, int.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<GitHubTrendingFetcher.TrendingRepo> result =
                (List<GitHubTrendingFetcher.TrendingRepo>) parse.invoke(fetcher, html, limit);
        return result;
    }
}
