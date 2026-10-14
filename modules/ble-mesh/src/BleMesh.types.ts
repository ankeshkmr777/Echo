export type BleMeshModuleEvents = {
  onChange: (params: { peerId: string; base64Bytes: string }) => void;
};

export type ChangeEventPayload = {
  value: string;
};
