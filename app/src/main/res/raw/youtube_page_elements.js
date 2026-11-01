function (options) {
    'use strict';
    if (window.top !== window || location.protocol !== 'https:'
            || (location.port && location.port !== '443')
            || !/^(www\.|m\.|music\.)?youtube\.com$/.test(location.hostname)) {
        return;
    }
    if (window.__ssyoutubePageElements) {
        window.__ssyoutubePageElements.update(options);
        return;
    }

    var styleId = 'ssyoutube-page-elements';
    var marker = 'data-ssyoutube-blocked-element';
    var marked = new Set();
    var timer = null;
    var comments = 'ytm-comment-section-renderer,ytm-comment-thread-renderer,'
        + 'ytm-comment-renderer,ytm-comment-replies-renderer,ytm-engagement-panel,'
        + 'ytm-engagement-panel-section-list-renderer,ytd-comments,#comments,'
        + 'ytd-comment-thread-renderer,ytd-comment-renderer';
    var cards = 'ytd-rich-item-renderer,ytd-video-renderer,ytd-grid-video-renderer,'
        + 'ytd-compact-video-renderer,yt-lockup-view-model,.ytLockupViewModelHost,'
        + 'ytm-rich-item-renderer,ytm-video-with-context-renderer,ytm-video-card-renderer,'
        + 'ytm-media-item,ytm-video-renderer,ytm-compact-video-renderer';
    var shelves = 'ytm-rich-section-renderer,ytd-rich-section-renderer,'
        + 'ytm-shelf-renderer,ytd-shelf-renderer,ytm-rich-shelf-renderer,'
        + 'ytd-rich-shelf-renderer,ytd-horizontal-card-list-renderer,'
        + 'ytm-reel-shelf-renderer,ytd-reel-shelf-renderer,.ytGridShelfViewModelHost';
    var navigation = 'ytm-pivot-bar-item-renderer,ytd-guide-entry-renderer,'
        + 'ytd-mini-guide-entry-renderer,yt-tab-shape,ytm-tab-renderer,.ytChipShapeHost';
    var badges = 'ytd-badge-supported-renderer,ytm-badge-supported-renderer,'
        + '.badge,.badge-shape-wiz,.yt-badge-shape,.ytBadgeShapeBadge,'
        + '.yt-content-metadata-view-model__metadata-text,'
        + '.yt-content-metadata-view-model-wiz__metadata-text,'
        + '.ytContentMetadataViewModelMetadataText,[badge-style]';
    var selectors = {
        shorts: 'ytm-reel-shelf-renderer,ytd-reel-shelf-renderer,'
            + 'ytm-shorts-lockup-view-model,ytm-shorts-lockup-view-model-v2,'
            + '.shortsLockupViewModelHost,.big-shorts-singleton,.pivot-shorts,'
            + 'ytd-reel-item-renderer',
        playables: 'ytd-mini-game-card-view-model,ytm-mini-game-card-view-model,'
            + 'ytd-rich-shelf-renderer[is-playables],ytm-rich-shelf-renderer[is-playables]',
        recommended: 'ytm-item-section-renderer[section-identifier="related-items"],'
            + 'ytd-watch-flexy #related,.ytm-player-endscreen,.ytp-endscreen-content,'
            + '.ytp-autonav-endscreen-upnext-container',
        end_video: '.ytp-ce-element,.ytp-ce-element-show',
        distracting: '.mobile-topbar-header-sign-in-button,'
            + '.mobile-topbar-header-content > ytm-menu,ytm-topbar-menu-button-renderer,'
            + '.pivot-bar-slide-in,ytm-comments-entry-point-header-renderer,'
            + 'ytm-item-section-renderer.single-column-watch-next-modern-panels,'
            + 'ytm-comment-section-renderer,ytm-comment-thread-renderer,'
            + 'ytm-comments-header-renderer,ytm-slim-video-metadata-section-renderer,'
            + 'ytd-comments,ytd-watch-metadata,ytd-video-primary-info-renderer,'
            + 'ytd-video-secondary-info-renderer,ytd-masthead #guide-button,'
            + 'ytd-masthead #end ytd-topbar-menu-button-renderer,'
            + 'ytd-masthead a[href^="https://accounts.google.com/ServiceLogin"]',
        thumbnails: 'ytd-thumbnail,.ytLockupViewModelContentImage,.ytThumbnailViewModelHost,'
            + '.shortsLockupViewModelHostThumbnailParentContainer,'
            + '.video-thumbnail-container-large,.media-item-thumbnail-container,'
            + '.video-thumbnail-container,ytm-thumbnail-cover,'
            + '#video-preview-container,ytm-video-preview'
    };

    function each(selector, root, callback) {
        Array.prototype.forEach.call(root.querySelectorAll(selector), callback);
    }

    function text(element) {
        return ((element.textContent || '') + ' '
            + (element.getAttribute('aria-label') || '') + ' '
            + (element.getAttribute('title') || '')).replace(/\s+/g, ' ').trim();
    }

    function protectedElement(element) {
        return !!element.closest(comments);
    }

    function linkKind(link) {
        var href = link.getAttribute('href');
        if (!href) {
            return '';
        }
        var url;
        try {
            url = new URL(href, location.href);
        } catch (error) {
            // Malformed page links are not blocking candidates.
            return '';
        }
        if (url.protocol !== 'https:' || (url.port && url.port !== '443')
                || !/^(www\.|m\.|music\.)?youtube\.com$/.test(url.hostname)) {
            return '';
        }
        if (/^\/shorts(?:\/|$)/.test(url.pathname)) {
            return 'shorts';
        }
        return /^\/(?:playables|gaming\/playables)(?:\/|$)/.test(url.pathname)
            ? 'playables' : '';
    }

    function apply() {
        if (timer !== null) {
            clearTimeout(timer);
            timer = null;
        }
        observer.disconnect();
        try {
            var next = new Set();
            var css = '';
            Object.keys(selectors).forEach(function (key) {
                if (options[key]) {
                    css += selectors[key] + '{display:none!important;}\n';
                }
            });
            if (options.shorts || options.playables) {
                each('a[href]', document, function (link) {
                    var kind = linkKind(link);
                    if (kind && options[kind] && !protectedElement(link)) {
                        next.add(link.closest('ytd-rich-item-renderer,ytm-rich-item-renderer')
                            || link.closest(cards) || link.closest(navigation)
                            || link.closest(shelves) || link);
                    }
                });
                each(shelves + ',' + navigation, document, function (container) {
                    if (protectedElement(container)) {
                        return;
                    }
                    var headings = container.querySelectorAll(
                        'h1,h2,h3,h4,[role="heading"],.yt-shelf-header-layout__title,'
                        + '.ytShelfHeaderLayoutTitle,.yt-tab-shape-wiz__tab,'
                        + '.ytTabShapeTab,.ytChipShapeChip,.pivot-bar-item-title,'
                        + 'yt-formatted-string.title');
                    Array.prototype.forEach.call(headings, function (heading) {
                        var label = text(heading);
                        if ((options.shorts && /^shorts$/i.test(label))
                                || (options.playables && /^(?:youtube )?playables$/i.test(label))) {
                            next.add(container);
                        }
                    });
                });
            }
            if (options.autodubbed || options.members_only) {
                each(cards, document, function (card) {
                    if (protectedElement(card)) {
                        return;
                    }
                    each(badges, card, function (badge) {
                        var label = text(badge);
                        var style = badge.getAttribute('badge-style') || badge.className || '';
                        if ((options.autodubbed && /\bauto[\s-]dubbed\b/i.test(label))
                                || (options.members_only && (
                                    /\bmembers[\s-]only\b|r\u00e9serv\u00e9 aux membres/i.test(label)
                                    || /BADGE_STYLE_TYPE_MEMBERS_ONLY|badge-style-type-members-only/
                                        .test(style)))) {
                            next.add(card.closest('ytd-rich-item-renderer,ytm-rich-item-renderer')
                                || card);
                        }
                    });
                });
            }
            if (options.recommended) {
                each('.feed-nudge-wrapper', document, function (nudge) {
                    if (/try searching to get started/i.test(text(nudge))) {
                        next.add(nudge);
                    }
                });
            }
            marked.forEach(function (element) {
                if (!next.has(element)) {
                    element.removeAttribute(marker);
                }
            });
            next.forEach(function (element) {
                if (!element.hasAttribute(marker)) {
                    element.setAttribute(marker, '');
                }
            });
            marked = next;
            if (next.size) {
                css += '[' + marker + ']{display:none!important;}';
            }
            var style = document.getElementById(styleId);
            if (!css) {
                if (style) {
                    style.remove();
                }
                return;
            }
            var parent = document.head || document.documentElement;
            if (!parent) {
                return;
            }
            if (!style) {
                style = document.createElement('style');
                style.id = styleId;
                parent.appendChild(style);
            }
            if (style.textContent !== css) {
                style.textContent = css;
            }
        } finally {
            observer.observe(document, {
                childList: true, subtree: true, characterData: true, attributes: true,
                attributeFilter: ['href', 'aria-label', 'title', 'class', 'badge-style',
                    'section-identifier', 'is-playables']
            });
        }
    }

    function schedule() {
        if (timer === null) {
            timer = setTimeout(apply, 50);
        }
    }

    var observer = new MutationObserver(schedule);
    window.__ssyoutubePageElements = {
        update: function (nextOptions) {
            options = nextOptions;
            apply();
        }
    };
    document.addEventListener('DOMContentLoaded', schedule);
    document.addEventListener('yt-navigate-finish', schedule);
    window.addEventListener('popstate', schedule);
    apply();
}
