# Pool Aim Auto Beta 0.5 — projeção de linhas após colisão

Esta versão foi ajustada para o comportamento descrito no vídeo: prolongar a mira nativa e, quando o jogo mostra o marcador de contato e as linhas de colisão, detectar cada direção separadamente.

## Alterações da versão 0.5

- O detector ainda procura a bola branca como origem da mira, mas agora dá mais peso a uma linha contínua que sai de uma bola redonda, reduzindo falsos positivos em letras, textos e detalhes da mesa.
- A captura agora é analisada em largura de 480 px, para preservar segmentos curtos que se perdiam na redução para 360 px.
- Adicionado um detector Hough leve para encontrar segmentos claros adicionais dentro da mesa.
- Uma linha adicional só é tratada como trajetória pós-colisão quando cruza a mira de entrada junto a um marcador circular claro, o que ajuda a descartar linhas aleatórias e textos.
- Quando são detectadas linhas de colisão, a projeção principal termina no ponto de contato e as direções secundárias são estendidas separadamente até as bordas, com rebotes geométricos.
- Projeções usam cores verde, magenta e azul para serem distintas da linha branca do jogo e reduzir a chance de o detector reconhecer o próprio desenho.
- A bolinha preta 8 permanece como controle flutuante para abrir/minimizar o menu.
- O painel informa quando detecta a mira e quantas trajetórias pós-colisão encontrou.
- A camada de desenho continua marcada como `NOT_TOUCHABLE`, para deixar os toques passarem ao jogo, sujeita às regras de sobreposição do Android.

## Limitações importantes

- A detecção continua experimental e depende de o jogo desenhar linhas claras e um marcador de contato reconhecível. Resolução, cores, compressão, animações, tema da mesa e brilho podem afetar os resultados.
- O detector segue linhas visíveis; não calcula sozinho a física completa de uma colisão se o jogo não exibir as linhas correspondentes.
- Os rebotes são estimativas geométricas retangulares. Não modelam com precisão os recortes das caçapas, o atrito, a força, o efeito ou a física interna do jogo.
- Os testes locais verificam o detector Java com imagens sintéticas. Eles não substituem a compilação Android nem o teste no aparelho. O APK deve ser gerado pelo GitHub Actions.

## Gerar o APK pelo GitHub Actions

1. Extraia o ZIP no computador.
2. No repositório GitHub, use **Add file → Upload files** e envie o conteúdo da pasta extraída, mantendo os caminhos e substituindo os arquivos de mesmo nome.
3. Em `.github/workflows/`, mantenha apenas `blank.yml` para evitar compilações duplicadas.
4. Faça **Commit changes**.
5. Abra **Actions** e aguarde **Build Pool Aim Auto Beta APK**.
6. Na execução concluída, abra **Summary → Artifacts** e baixe `pool-aim-auto-v05-debug-apk`.
7. Extraia o ZIP do artefato e instale `app-debug.apk` no Android.

O workflow usa Java 17, Android SDK 35 e Gradle 8.9. Não é necessário instalar Android Studio no Windows Server para compilar na nuvem.
