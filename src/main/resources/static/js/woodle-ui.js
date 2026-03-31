(function () {
    if (window.woodleUiBound) {
        return;
    }

    const timeRowCount = function (container) {
        return container.querySelectorAll("[data-time-row]").length;
    };

    const updateRemoveButtonState = function (form) {
        if (!form) {
            return;
        }
        const container = form.querySelector("#admin-start-times");
        const removeButton = form.querySelector("#admin-remove-time");
        if (!container || !removeButton) {
            return;
        }
        removeButton.disabled = timeRowCount(container) <= 1;
    };

    const resetToSingleTimeRow = function (form) {
        if (!form) {
            return;
        }
        const container = form.querySelector("#admin-start-times");
        if (!container) {
            return;
        }
        const rows = container.querySelectorAll("[data-time-row]");
        for (let i = 1; i < rows.length; i++) {
            rows[i].remove();
        }
        updateRemoveButtonState(form);
    };

    const createTimeRow = function () {
        const row = document.createElement("div");
        row.className = "form-row";
        row.setAttribute("data-time-row", "true");

        const label = document.createElement("label");
        label.textContent = "Startzeit";

        const input = document.createElement("input");
        input.name = "startTime";
        input.type = "time";
        input.required = true;

        row.appendChild(label);
        row.appendChild(input);
        return row;
    };

    const refreshAdminTimeControls = function () {
        document.querySelectorAll("form#admin-options-form").forEach(function (form) {
            updateRemoveButtonState(form);
        });
    };

    const measureParticipantOverflow = function (wrap) {
        const table = wrap ? wrap.querySelector(".votes-table") : null;
        if (!wrap || !table) {
            return false;
        }
        const tableWidth = Math.ceil(table.getBoundingClientRect().width);
        const wrapWidth = Math.floor(wrap.getBoundingClientRect().width);
        return tableWidth > wrapWidth + 1 || (wrap.scrollWidth - wrap.clientWidth) > 1;
    };

    const syncParticipantScrollEdge = function (wrap) {
        if (!wrap) {
            return;
        }
        const maxScroll = wrap.scrollWidth - wrap.clientWidth;
        const hint = wrap.parentElement ? wrap.parentElement.querySelector(".scroll-hint--participant") : null;
        const hasHorizontalOverflow = measureParticipantOverflow(wrap);
        if (hint) {
            hint.hidden = !hasHorizontalOverflow;
        }
        wrap.classList.toggle("is-at-end", wrap.scrollLeft >= maxScroll - 2);
    };

    const syncParticipantHintState = function (wrap) {
        syncParticipantScrollEdge(wrap);
        updateParticipantHintLayout();
    };

    const bindParticipantScrollHint = function () {
        document.querySelectorAll(".votes-table-wrap--participant").forEach(function (wrap) {
            if (wrap.dataset.scrollHintBound === "true") {
                syncParticipantHintState(wrap);
                return;
            }
            wrap.dataset.scrollHintBound = "true";
            wrap.addEventListener("scroll", function () {
                syncParticipantScrollEdge(wrap);
            }, {passive: true});
            const scheduleSync = function () {
                window.requestAnimationFrame(function () {
                    syncParticipantHintState(wrap);
                });
            };
            if (typeof ResizeObserver !== "undefined") {
                const observer = new ResizeObserver(function () {
                    scheduleSync();
                });
                observer.observe(wrap);
                const table = wrap.querySelector(".votes-table");
                if (table) {
                    observer.observe(table);
                }
            }
            scheduleSync();
            window.setTimeout(scheduleSync, 120);
        });
    };

    const updateParticipantHintLayout = function () {
        document.querySelectorAll(".votes-table-wrap--participant").forEach(function (wrap) {
            const hint = wrap.parentElement ? wrap.parentElement.querySelector(".scroll-hint--participant") : null;
            const leftSticky = wrap.querySelector("thead .votes-table__sticky-left");
            const rightSticky = wrap.querySelector("thead .votes-table__sticky-right");
            if (!hint || !leftSticky || !rightSticky) {
                return;
            }
            const wrapWidth = wrap.getBoundingClientRect().width;
            const leftWidth = leftSticky.getBoundingClientRect().width;
            const rightWidth = rightSticky.getBoundingClientRect().width;
            const availableWidth = Math.max(wrapWidth - leftWidth - rightWidth, 0);
            hint.style.marginLeft = leftWidth + "px";
            hint.style.width = availableWidth + "px";
            hint.style.maxWidth = availableWidth + "px";
        });
    };

    document.addEventListener("click", async function (event) {
        const copyButton = event.target.closest("[data-copy-target]");
        if (copyButton) {
            const targetId = copyButton.getAttribute("data-copy-target");
            const target = targetId ? document.getElementById(targetId) : null;
            if (!target) {
                return;
            }
            const value = target.textContent || "";
            try {
                await navigator.clipboard.writeText(value);
            } catch (_) {
                const range = document.createRange();
                range.selectNodeContents(target);
                const selection = window.getSelection();
                if (!selection) {
                    return;
                }
                selection.removeAllRanges();
                selection.addRange(range);
                document.execCommand("copy");
                selection.removeAllRanges();
            }
            return;
        }
        
        const addButton = event.target.closest("#admin-add-time");
        if (addButton) {
            const form = addButton.closest("form#admin-options-form");
            if (!form) {
                return;
            }
            const container = form.querySelector("#admin-start-times");
            if (!container) {
                return;
            }
            container.appendChild(createTimeRow());
            updateRemoveButtonState(form);
            return;
        }

        const removeButton = event.target.closest("#admin-remove-time");
        if (!removeButton) {
            return;
        }
        const form = removeButton.closest("form#admin-options-form");
        if (!form) {
            return;
        }
        const container = form.querySelector("#admin-start-times");
        if (!container) {
            return;
        }
        const rows = container.querySelectorAll("[data-time-row]");
        if (rows.length > 1) {
            rows[rows.length - 1].remove();
        }
        updateRemoveButtonState(form);
    });

    document.body.addEventListener("htmx:afterRequest", function (event) {
        const form = event.target && event.target.closest ? event.target.closest("form#admin-options-form") : null;
        if (!form || !event.detail || !event.detail.successful) {
            return;
        }
        resetToSingleTimeRow(form);
    });

    document.body.addEventListener("htmx:afterSwap", function () {
        refreshAdminTimeControls();
        bindParticipantScrollHint();
        updateParticipantHintLayout();
    });

    window.WoodleUi = {
        refreshAdminTimeControls: refreshAdminTimeControls,
        bindParticipantScrollHint: bindParticipantScrollHint,
        updateParticipantHintLayout: updateParticipantHintLayout
    };
    window.woodleUiBound = true;
    refreshAdminTimeControls();
    bindParticipantScrollHint();
    updateParticipantHintLayout();
    window.addEventListener("resize", function () {
        document.querySelectorAll(".votes-table-wrap--participant").forEach(function (wrap) {
            syncParticipantScrollEdge(wrap);
        });
        updateParticipantHintLayout();
    });
})();
