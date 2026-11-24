'use strict';

/* Mascara de CPF (mesma do aproveitamento). O servidor aceita o CPF com ou sem mascara. */
document.querySelectorAll('[data-mascara=cpf]').forEach(input => {
    input.addEventListener('input', () => {
        const d = input.value.replace(/\D/g, '').slice(0, 11);
        input.value = d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
            .replace(/\.(\d{3})(\d)/, '.$1-$2');
    });
});
