# Pool Aim Auto Beta 0.6.1 — correção de linhas invisíveis

Esta revisão corrige uma regressão da versão 0.6: a mira podia ser detectada, mas nenhuma extensão era desenhada.

## Alterações objetivas

1. **Guia presente = modo mira**: o detector de movimento não deve mais confundir o movimento do taco com uma tacada quando a guia nativa foi reconhecida. Quando a guia some, o app remove as linhas imediatamente e aguarda uma guia nova, evitando desenhar durante o movimento.
2. **Colisão sem ramos não deixa a tela vazia**: o 0.6 entrava no modo de colisão mesmo com 0 ramificações; o renderizador então não desenhava nada. Agora, nesses casos, prolonga provisoriamente apenas a linha principal. Isso é apenas uma aproximação; não significa que a trajetória pós-colisão foi detectada.
3. **Diagnóstico no menu da bolinha 8**: botão **Teste da linha branca (6 s)** mostra uma linha e um círculo brancos diretamente na sobreposição, sem precisar detectar o jogo. Isto ajuda a separar falha de permissão/desenho de falha na análise da captura.
4. **Status com contadores**: o menu mostra número de imagens analisadas, quantas tiveram guia detectada e o tamanho da captura. Se a contagem de guias for zero mesmo mirando, confirme que escolheu **tela inteira** ao autorizar a captura e envie foto do menu.
5. Mantém ícone bola 8, desenho em branco fino, calculadora de tabelas aproximada, nenhuma seleção manual.

## Como testar

1. Instale o APK compilado pelo GitHub Actions a partir deste ZIP.
2. Abra o aplicativo; permita sobreposição e **inicie reconhecimento**; se Android perguntar entre app individual e tela inteira, prefira **Tela inteira**.
3. Abra o 8 Ball Pool, toque na bolinha 8 e escolha **Teste da linha branca (6 s)**. Uma linha branca com o texto TESTE DE LINHA deve aparecer sobre o jogo; ela é apenas um teste visual temporário.
4. Mire com o taco parado e veja o menu: `Imagens` deverá aumentar, `linhas` deverá aumentar quando o detector reconhecer a guia.
5. Faça uma tacada: a projeção deve desaparecer quando a guia original deixar de ser detectada. O novo detector ainda depende das linhas que o jogo efetivamente renderiza; a identificação de dois ramos e a precisão das tabelas permanecem experimentais.

## Compilação (sem Android Studio)

Envie os arquivos para seu repositório 8Ball no GitHub e faça Commit changes. Em **Actions** rode `Build Pool Aim Auto Beta 0.6.1 APK`. Na execução verde, abra **Summary → Artifacts** e baixe `pool-aim-auto-v061-debug-apk`, extraia e instale `app-debug.apk` no celular.

Limitações: testes locais de lógica não validam a captura MediaProjection e a sobreposição no dispositivo. Não foi possível compilar um APK Android completo neste ambiente. Se a assinatura for diferente da instalada, pode ser necessária a desinstalação antes de instalar a nova (isso remove os dados locais deste app auxiliar).
