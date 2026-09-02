document.querySelector('form')?.addEventListener('submit', (event) => {
    const form = event.currentTarget;
    if (!form.checkValidity()) {
        event.preventDefault();
        form.reportValidity();
    }
});