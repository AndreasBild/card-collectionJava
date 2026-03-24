<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
</head>
<body>
${topNavHtml?no_esc}
<main class="detail-main">
    <div class="collection-table-wrapper"
         style="background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.05);">
        ${pageContent?no_esc}
    </div>
</main>

<script>
    document.addEventListener('DOMContentLoaded', () => {
        // --- Scroll Restoration Logic ---
        const savedCardId = sessionStorage.getItem('lastClickedCard');
        if (savedCardId) {
            const targetRow = document.getElementById(savedCardId);
            if (targetRow) {
                setTimeout(() => {
                    targetRow.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    targetRow.style.backgroundColor = '#ffffcc';
                    setTimeout(() => {
                        targetRow.style.backgroundColor = '';
                    }, 2000);
                }, 100);
            }
            sessionStorage.removeItem('lastClickedCard');
        }

        document.querySelectorAll('.collection-table-wrapper table').forEach(table => {
            // Capture click for scroll restoration
            table.addEventListener('click', (e) => {
                const row = e.target.closest('tr');
                if (row && row.id) {
                    sessionStorage.setItem('lastClickedCard', row.id);
                }
            });
        });
    });
</script>

${footerHtml?no_esc}
</body>
</html>