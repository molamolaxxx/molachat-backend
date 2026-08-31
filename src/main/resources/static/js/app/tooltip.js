(function () {
    var TOOLTIP_GAP = 5

    function positionTooltip(target) {
        var tooltipId = target.getAttribute('data-tooltip-id')
        var tooltip = tooltipId && document.getElementById(tooltipId)
        if (!tooltip || tooltip.style.display === 'none') {
            return
        }

        var $tooltip = $(tooltip)
        var targetRect = target.getBoundingClientRect()
        var tooltipWidth = $tooltip.outerWidth()
        var tooltipHeight = $tooltip.outerHeight()
        var targetLeft = targetRect.left + window.pageXOffset
        var targetTop = targetRect.top + window.pageYOffset
        var position = target.getAttribute('data-position') || 'bottom'
        var left
        var top

        if (position === 'top') {
            left = targetLeft + targetRect.width / 2 - tooltipWidth / 2
            top = targetTop - tooltipHeight - TOOLTIP_GAP
        } else if (position === 'left') {
            left = targetLeft - tooltipWidth - TOOLTIP_GAP
            top = targetTop + targetRect.height / 2 - tooltipHeight / 2
        } else if (position === 'right') {
            left = targetLeft + targetRect.width + TOOLTIP_GAP
            top = targetTop + targetRect.height / 2 - tooltipHeight / 2
        } else {
            left = targetLeft + targetRect.width / 2 - tooltipWidth / 2
            top = targetTop + targetRect.height + TOOLTIP_GAP
        }

        left = Math.max(window.pageXOffset + 4,
            Math.min(left, window.pageXOffset + window.innerWidth - tooltipWidth))
        top = Math.max(window.pageYOffset + 4,
            Math.min(top, window.pageYOffset + window.innerHeight - tooltipHeight))
        $tooltip.css({left: left, top: top})
    }

    $(document).ready(function () {
        // Materialize appends tooltips to body, whose zoom would scale their screen coordinates twice.
        $('.material-tooltip').appendTo(document.documentElement)
        $('.tooltipped').each(function () {
            var target = this
            var timer
            $(target).off('.zoomAwareTooltip').on({
                'mouseenter.zoomAwareTooltip': function () {
                    var delay = parseInt(target.getAttribute('data-delay'), 10)
                    if (isNaN(delay)) {
                        delay = 350
                    }
                    clearTimeout(timer)
                    timer = setTimeout(function () {
                        positionTooltip(target)
                    }, delay)
                },
                'mouseleave.zoomAwareTooltip': function () {
                    clearTimeout(timer)
                }
            })
        })
    })
})()
