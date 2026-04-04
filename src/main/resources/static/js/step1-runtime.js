(function () {

    function normalizeTextValue(value) {
        return value ? value.trim() : "";
    }

    function normalizePollTitle(value) {
        return value ? value.trim() : "";
    }

    function writeStoredValue(key, value) {
        if (!window.localStorage) {
            return;
        }
        try {
            const normalized = normalizeTextValue(value);
            if (normalized) {
                window.localStorage.setItem(key, normalized);
            } else {
                window.localStorage.removeItem(key);
            }
        } catch (error) {
            // Ignore storage errors (private mode / blocked storage).
        }
    }

    function readStoredValue(key) {
        if (!window.localStorage) {
            return "";
        }
        try {
            return normalizeTextValue(window.localStorage.getItem(key) || "");
        } catch (error) {
            return "";
        }
    }

    function writeStoredBoolean(key, value) {
        if (!window.localStorage) {
            return;
        }
        try {
            window.localStorage.setItem(key, value ? "true" : "false");
        } catch (error) {
            // Ignore storage errors (private mode / blocked storage).
        }
    }

    function readStoredBoolean(key) {
        if (!window.localStorage) {
            return false;
        }
        try {
            return window.localStorage.getItem(key) === "true";
        } catch (error) {
            return false;
        }
    }

    function readStoredBooleanWithDefault(key, defaultValue) {
        if (!window.localStorage) {
            return defaultValue;
        }
        try {
            const stored = window.localStorage.getItem(key);
            return stored !== null ? stored === "true" : defaultValue;
        } catch (error) {
            return defaultValue;
        }
    }

    window.initStep1 = function (options) {
        const emailEnabled = !!(options && options.emailEnabled);
        const base = (options && options.backendBase)
            ? String(options.backendBase).replace(/\/+$/, "") : "";

        const step1Form = document.getElementById("step1-form");
        if (!step1Form) {
            return;
        }
        const activePollCount = document.getElementById("active-poll-count");
        const loadingIndicator = document.getElementById("step1-loading-indicator");
        const authorNameInput = document.getElementById("author-name");
        const pollTitleInput = document.getElementById("poll-title");
        const descriptionInput = document.getElementById("poll-description");
        const notifyOnVoteInput = document.querySelector("input[name='notifyOnVote']");
        const notifyOnCommentInput = document.querySelector("input[name='notifyOnComment']");
        const pollTitleHistory = document.getElementById("poll-title-history");

        const authorNameStorageKey = "woodle.poll.step1.authorName";
        const authorEmailStorageKey = "woodle.poll.step1.authorEmail";
        const pollTitleStorageKey = "woodle.poll.step1.titleHistory";
        const pollTitleCurrentStorageKey = "woodle.poll.step1.pollTitle";
        const descriptionStorageKey = "woodle.poll.step1.description";
        const notifyOnVoteStorageKey = "woodle.poll.step1.notifyOnVote";
        const notifyOnCommentStorageKey = "woodle.poll.step1.notifyOnComment";
        const maxPollTitleSuggestions = 8;
        const maxTransientRetries = 10;
        const transientRetryDelayMs = 1000;
        const maxActivePollCountRetries = 10;
        const activePollCountRetryDelayMs = 1000;

        let transientRetryCount = 0;
        let retryInFlight = false;
        let activePollCountRetryCount = 0;
        let loadingHintTimer = null;

        function setLoadingHintVisible(visible) {
            if (!loadingIndicator) {
                return;
            }
            loadingIndicator.classList.toggle("step1-loading-indicator--visible", visible);
        }

        function clearLoadingHintTimer() {
            if (loadingHintTimer !== null) {
                clearTimeout(loadingHintTimer);
                loadingHintTimer = null;
            }
        }

        function readPollTitleHistory() {
            if (!window.localStorage) {
                return [];
            }
            try {
                const raw = window.localStorage.getItem(pollTitleStorageKey);
                if (!raw) {
                    return [];
                }
                const parsed = JSON.parse(raw);
                if (!Array.isArray(parsed)) {
                    return [];
                }
                return parsed
                        .filter(function (item) { return typeof item === "string"; })
                        .map(normalizePollTitle)
                        .filter(function (item) { return item.length > 0; });
            } catch (error) {
                return [];
            }
        }

        function writePollTitleHistory(values) {
            if (!window.localStorage) {
                return;
            }
            try {
                window.localStorage.setItem(pollTitleStorageKey, JSON.stringify(values));
            } catch (error) {
                // Ignore storage errors (private mode / blocked storage).
            }
        }

        function renderPollTitleHistory(values) {
            if (!pollTitleHistory) {
                return;
            }
            pollTitleHistory.innerHTML = "";
            values.forEach(function (value) {
                const option = document.createElement("option");
                option.value = value;
                pollTitleHistory.appendChild(option);
            });
        }

        function rememberPollTitle(value) {
            const normalized = normalizePollTitle(value);
            writeStoredValue(pollTitleCurrentStorageKey, normalized);
            if (!normalized) {
                return;
            }
            const existing = readPollTitleHistory().filter(function (item) {
                return item !== normalized;
            });
            const updated = [normalized].concat(existing).slice(0, maxPollTitleSuggestions);
            writePollTitleHistory(updated);
            renderPollTitleHistory(updated);
        }

        // Always re-query the email input to support HTMX field swap (template case).
        function getEmailInput() {
            return document.getElementById("author-email");
        }

        // Disable notification checkboxes when email is not available.
        if (!emailEnabled) {
            if (notifyOnVoteInput) { notifyOnVoteInput.disabled = true; }
            if (notifyOnCommentInput) { notifyOnCommentInput.disabled = true; }
        }

        // Re-wire form action and active-count endpoint when a backend base URL is provided.
        if (base) {
            step1Form.action = base + "/poll/step-2";
            step1Form.setAttribute("hx-post", base + "/poll/step-2");
            if (activePollCount) {
                activePollCount.setAttribute("hx-get", base + "/poll/active-count");
                if (window.htmx && typeof window.htmx.process === "function") {
                    window.htmx.process(activePollCount);
                }
            }
        }

        // Initialise fields from storage.
        renderPollTitleHistory(readPollTitleHistory());
        if (authorNameInput && !authorNameInput.value) {
            authorNameInput.value = readStoredValue(authorNameStorageKey);
        }
        const initialEmailInput = getEmailInput();
        if (initialEmailInput && !initialEmailInput.value) {
            initialEmailInput.value = readStoredValue(authorEmailStorageKey);
        }
        if (pollTitleInput && !pollTitleInput.value) {
            pollTitleInput.value = readStoredValue(pollTitleCurrentStorageKey);
        }
        if (descriptionInput && !descriptionInput.value) {
            descriptionInput.value = readStoredValue(descriptionStorageKey);
        }
        if (notifyOnVoteInput && emailEnabled) {
            notifyOnVoteInput.checked = readStoredBooleanWithDefault(notifyOnVoteStorageKey, true);
        }
        if (notifyOnCommentInput && emailEnabled) {
            notifyOnCommentInput.checked = readStoredBooleanWithDefault(notifyOnCommentStorageKey, true);
        }

        // Persist form state on submit.
        step1Form.addEventListener("submit", function () {
            if (authorNameInput) {
                writeStoredValue(authorNameStorageKey, authorNameInput.value);
            }
            const currentEmailInput = getEmailInput();
            if (currentEmailInput) {
                writeStoredValue(authorEmailStorageKey, currentEmailInput.value);
            }
            if (pollTitleInput) {
                rememberPollTitle(pollTitleInput.value);
            }
            if (descriptionInput) {
                writeStoredValue(descriptionStorageKey, descriptionInput.value);
            }
            if (notifyOnVoteInput) {
                writeStoredBoolean(notifyOnVoteStorageKey, notifyOnVoteInput.checked);
            }
            if (notifyOnCommentInput) {
                writeStoredBoolean(notifyOnCommentStorageKey, notifyOnCommentInput.checked);
            }
            if (!retryInFlight) {
                transientRetryCount = 0;
            }
        });

        // Show loading hint with a short delay so fast responses do not flicker.
        document.body.addEventListener("htmx:beforeRequest", function (event) {
            if (event.target !== step1Form) {
                return;
            }
            clearLoadingHintTimer();
            setLoadingHintVisible(false);
            loadingHintTimer = setTimeout(function () {
                setLoadingHintVisible(true);
                loadingHintTimer = null;
            }, 200);
        });

        // Transient retry for step-1 form submit (502/503/504).
        document.body.addEventListener("htmx:responseError", function (event) {
            if (event.target !== step1Form) {
                return;
            }
            const status = event.detail && event.detail.xhr ? event.detail.xhr.status : 0;
            const isTransient = status === 502 || status === 503 || status === 504;
            if (!isTransient || transientRetryCount >= maxTransientRetries) {
                clearLoadingHintTimer();
                setLoadingHintVisible(false);
                return;
            }
            transientRetryCount += 1;
            retryInFlight = true;
            setTimeout(function () {
                step1Form.requestSubmit();
                retryInFlight = false;
            }, transientRetryDelayMs);
        });

        // Clear loading hint and track active-count success.
        document.body.addEventListener("htmx:afterRequest", function (event) {
            if (event.target === step1Form) {
                clearLoadingHintTimer();
                setLoadingHintVisible(false);
            }
            if (event.target === step1Form && event.detail && event.detail.successful) {
                transientRetryCount = 0;
                retryInFlight = false;
            }
            const requestPath = event.detail && event.detail.requestConfig ? event.detail.requestConfig.path : "";
            const isActiveCountRequest = (event.target === activePollCount)
                    || requestPath === "/poll/active-count"
                    || requestPath.endsWith("/poll/active-count");
            if (isActiveCountRequest && event.detail && event.detail.successful) {
                activePollCountRetryCount = 0;
            }
        });

        // Transient retry for active poll count (502/503/504).
        document.body.addEventListener("htmx:responseError", function (event) {
            const requestPath = event.detail && event.detail.requestConfig ? event.detail.requestConfig.path : "";
            if (event.target !== activePollCount
                    && requestPath !== "/poll/active-count"
                    && !requestPath.endsWith("/poll/active-count")) {
                return;
            }
            const status = event.detail && event.detail.xhr ? event.detail.xhr.status : 0;
            const isTransient = status === 502 || status === 503 || status === 504;
            if (!isTransient || activePollCountRetryCount >= maxActivePollCountRetries) {
                return;
            }
            activePollCountRetryCount += 1;
            const activeCountEndpoint = activePollCount
                    ? activePollCount.getAttribute("hx-get")
                    : requestPath;
            setTimeout(function () {
                if (window.htmx && typeof window.htmx.ajax === "function" && activeCountEndpoint) {
                    window.htmx.ajax("GET", activeCountEndpoint, {
                        target: activePollCount || "#active-poll-count",
                        swap: "innerHTML"
                    });
                }
            }, activePollCountRetryDelayMs);
        });

        // Restore email value after HTMX swaps the email field (template/server-rendered case).
        document.body.addEventListener("htmx:afterSwap", function (event) {
            if (!(event.target && event.target.id === "author-email-field")) {
                return;
            }
            const currentEmailInput = getEmailInput();
            if (currentEmailInput && !currentEmailInput.value) {
                currentEmailInput.value = readStoredValue(authorEmailStorageKey);
            }
        });

        // Blur and change persistence handlers.
        if (pollTitleInput) {
            pollTitleInput.addEventListener("blur", function () {
                rememberPollTitle(pollTitleInput.value);
            });
        }
        if (descriptionInput) {
            descriptionInput.addEventListener("blur", function () {
                writeStoredValue(descriptionStorageKey, descriptionInput.value);
            });
        }
        if (notifyOnVoteInput) {
            notifyOnVoteInput.addEventListener("change", function () {
                writeStoredBoolean(notifyOnVoteStorageKey, notifyOnVoteInput.checked);
            });
        }
        if (notifyOnCommentInput) {
            notifyOnCommentInput.addEventListener("change", function () {
                writeStoredBoolean(notifyOnCommentStorageKey, notifyOnCommentInput.checked);
            });
        }
        if (authorNameInput) {
            authorNameInput.addEventListener("blur", function () {
                writeStoredValue(authorNameStorageKey, authorNameInput.value);
            });
        }
        // Delegated blur for email — works even after HTMX replaces the field.
        document.body.addEventListener("blur", function (event) {
            if (!(event.target && event.target.id === "author-email")) {
                return;
            }
            writeStoredValue(authorEmailStorageKey, event.target.value);
        }, true);
    };

})();
