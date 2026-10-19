# Pool Aim Auto Beta 0.6.2 (experimental)

Versão baseada no ZIP 0.6.1 fornecido na conversa. Atualizada especificamente para leitura das duas linhas de saída no impacto; a linha do taco e os controles de sobreposição permanecem.

## Correções nesta versão

- Substitui a busca global de segmentos brancos por um rastreamento de raios curtos na vizinhança do possível ponto de colisão.
- Detecta traços de colisão mais curtos/fracos e distingue os dois ramos a partir do mesmo ponto. Exige evidência visual: não calcula trajetórias de saída fictícias quando a tela não mostra os ramos.
- Filtra pontos claros que sejam só o tecido da mesa, um risco reto ou uma decoração; verifica contraste em relação ao fundo e objeto circular próximo.
- Adiciona diagnósticos ao menu: **Imagens / guia / impactos / duplos**. Esses contadores ajudam a distinguir se o problema é na detecção ou na projeção.
- Mantém as projeções brancas finas, a bolinha 8 para minimizar, passagem de toques, projeção das tabelas e a regra de ocultação durante movimento.

**Limitações:** A detecção continua experimental. Exemplos desenhados/sintéticos não provam precisão no 8 Ball Pool. Alguns temas de mesa, efeitos de tela e tamanhos do aparelho podem gerar falsos positivos ou perder linhas. Não é previsão física perfeita: força, efeito e atrito alteram o movimento real; reflexos nas tabelas são apenas aproximações geométricas.

## Compilar sem Android Studio

1. Extraia o ZIP.
2. No mesmo repositório GitHub, envie os arquivos extraídos mantendo as pastas da raiz (substitua os equivalentes); não envie o ZIP como arquivo isolado.
3. Faça Commit changes na branch **main**.
4. Em Actions abra **Build Pool Aim Auto Beta 0.6.2 APK** e acompanhe a execução mais recente.
5. Se a execução ficar verde, abra Summary > Artifacts > **pool-aim-auto-v062-debug-apk**, extraia e instale o app-debug.apk.
6. Se houver conflito de assinatura, desinstale a versão anterior; as configurações locais podem ser perdidas.

## Teste em jogo e diagnóstico

- Abra o jogo, inicie captura de **tela inteira**, mire sem tocar manualmente em nenhuma bola.
- Com a bolinha 8, abra o menu e observe o status ao apontar o taco para uma bola.
- **Mira detectada:** só reconheceu a linha principal; **Colisão • 1 ou 2 saída(s):** encontrou ramos; **impactos / duplos:** contadores de frames identificados.
- Se só reconhecer a linha do taco, envie **gravação de tela da 0.6.2 com a mira na bola e o menu mostrando os números**. É o diagnóstico necessário para calibrar os limiares para a sua mesa, sem inventar mais linhas.

## Verificação executada neste ambiente

O Java do detector e os testes Java da lógica passaram em testes locais de unidade. **APK Android não foi compilado aqui**; o GitHub Actions fará a compilação e os testes Gradle. Não houve teste em dispositivo real da versão 0.6.2.
