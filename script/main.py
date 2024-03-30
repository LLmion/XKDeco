import json
import os
from pathlib import Path

from BlockTemplateProvider import BlockTemplateProvider
from CreativeTabProvider import CreativeTabProvider
from TableDataProvider import TableDataProvider
from BlockDefinitionProvider import BlockDefinitionProvider
from MaterialProvider import MaterialProvider
from Pack import Pack
from TagsProvider import TagsProvider
from TranslationProvider import TranslationProvider


def main():
    configPath = 'config.json'
    if len(os.sys.argv) > 1:
        configPath = os.sys.argv[1]
    if not Path(configPath).exists():
        if Path('script/' + configPath).exists():
            os.chdir('script')
        else:
            print(configPath + ' not found')
            return

    with open(configPath, encoding='utf-8') as f:
        config = json.load(f)
    pack = Pack(config)
    pack.addProvider(MaterialProvider(pack))
    pack.addProvider(BlockTemplateProvider(pack))
    pack.addProvider(BlockDefinitionProvider(pack))
    pack.addProvider(CreativeTabProvider(pack))
    pack.addProvider(TranslationProvider(pack))
    pack.addProvider(TagsProvider(pack, 'block', 'blocks'))
    pack.finish()


if __name__ == '__main__':
    main()
