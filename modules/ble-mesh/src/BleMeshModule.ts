import { NativeModule, requireNativeModule } from 'expo';

import { BleMeshModuleEvents } from './BleMesh.types';

declare class BleMeshModule extends NativeModule<BleMeshModuleEvents> {
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

export default requireNativeModule<BleMeshModule>('BleMesh');
