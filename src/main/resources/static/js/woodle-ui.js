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

    const syncParticipantScrollEdge = function (wrap) {
        if (!wrap) {
            return;
        }
        const maxScroll = wrap.scrollWidth - wrap.clientWidth;
        wrap.classList.toggle("is-at-end", wrap.scrollLeft >= maxScroll - 2);
    };

    const bindParticipantScrollHint = function () {
        document.querySelectorAll(".votes-table-wrap--participant").forEach(function (wrap) {
            if (wrap.dataset.scrollHintBound === "true") {
                syncParticipantScrollEdge(wrap);
                return;
            }
            wrap.dataset.scrollHintBound = "true";
            wrap.addEventListener("scroll", function () {
                syncParticipantScrollEdge(wrap);
            }, {passive: true});
            syncParticipantScrollEdge(wrap);
        });
    };

    const updateParticipantHintLayout = function () {
        document.querySelectorAll(".votes-table-wrap--participant").forEach(function (wrap) {
            const hint = wrap.querySelector(".scroll-hint--participant");
            const leftSticky = wrap.querySelector(".votes-table__sticky-left");
            const rightSticky = wrap.querySelector(".votes-table__sticky-right");
            if (!hint || !leftSticky || !rightSticky) {
                return;
            }
            const leftWidth = leftSticky.getBoundingClientRect().width;
            const rightWidth = rightSticky.getBoundingClientRect().width;
            hint.style.left = leftWidth + "px";
            hint.style.width = "calc(100% - " + leftWidth + "px - " + rightWidth + "px)";
            hint.style.maxWidth = "calc(100% - " + leftWidth + "px - " + rightWidth + "px)";
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
