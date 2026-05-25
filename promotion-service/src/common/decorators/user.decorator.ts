import { createParamDecorator, ExecutionContext } from '@nestjs/common';

export interface UserContext {
  userId: string;
  userRole: string;
  userEmail: string;
}

export const User = createParamDecorator(
  (field: keyof UserContext | undefined, ctx: ExecutionContext) => {
    const request = ctx.switchToHttp().getRequest();

    const user: UserContext = {
      userId: request.headers['x-user-id'],
      userRole: request.headers['x-user-role'],
      userEmail: request.headers['x-user-email'],
    };

    return field ? user[field] : user;
  },
);
